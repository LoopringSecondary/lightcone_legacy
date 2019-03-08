/*
 * Copyright 2018 Loopring Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.lightcone.relayer.support

import java.util.concurrent.TimeUnit
import akka.pattern._
import io.lightcone.relayer.actors._
import io.lightcone.relayer.ethereum._
import io.lightcone.relayer.validator._
import io.lightcone.relayer.data._
import io.lightcone.relayer._
import io.lightcone.ethereum.abi._
import io.lightcone.ethereum._
import io.lightcone.core._
import io.lightcone.lib._
import org.rnorth.ducttape.TimeoutException
import org.rnorth.ducttape.unreliables.Unreliables
import org.testcontainers.containers.ContainerLaunchException
import org.web3j.crypto.Credentials
import org.web3j.utils.Numeric
import scala.concurrent.Await

trait EthereumSupport extends DatabaseModuleSupport {
  me: CommonSpec =>

  implicit val rb = new EthereumCallRequestBuilder
  implicit val brb = new EthereumBatchCallRequestBuilder
  val orderCancellerAbi = OrderCancellerAbi()

  val orderCancelAddress =
    config.getString("loopring_protocol.order-cancel-address")

  actors.add(EthereumQueryActor.name, EthereumQueryActor.start)
  actors.add(
    EthereumQueryMessageValidator.name,
    MessageValidationActor(
      new EthereumQueryMessageValidator(),
      EthereumQueryActor.name,
      EthereumQueryMessageValidator.name
    )
  )

  if (!actors.contains(GasPriceActor.name)) {
    actors.add(GasPriceActor.name, GasPriceActor.start)
  }

  val poolSize =
    config.getConfig(EthereumClientMonitor.name).getInt("pool-size")

  HttpConnector.start.foreach {
    case (name, actor) => actors.add(name, actor)
  }

  val connectionPools = HttpConnector
    .connectorNames(config)
    .map {
      case (nodeName, node) =>
        actors.get(nodeName)
    }
    .toSeq

  val blockNumJsonRpcReq = JsonRpc.Request(
    "{\"jsonrpc\":\"2.0\",\"method\":\"eth_blockNumber\",\"params\":[],\"id\":64}"
  )

  //必须等待connectionPools启动完毕才能启动monitor和accessActor
  try Unreliables.retryUntilTrue(10, TimeUnit.SECONDS, () => {
    val f =
      (connectionPools(0) ? blockNumJsonRpcReq).mapTo[JsonRpc.Response]
    val res = Await.result(f, timeout.duration)
    res.json != ""
  })
  catch {
    case e: TimeoutException =>
      throw new ContainerLaunchException(
        "Timed out waiting for connectionPools init.)"
      )
  }

  implicit val rbg: RingBatchGenerator = new Protocol2RingBatchGenerator

  actors.add(EthereumClientMonitor.name, EthereumClientMonitor.start)
  Thread.sleep(1000)

  actors.add(EthereumAccessActor.name, EthereumAccessActor.start)

  actors.add(
    ChainReorganizationManagerActor.name,
    ChainReorganizationManagerActor.start
  )

  def transferEth(
      to: String,
      amountStr: String
    )(
      implicit
      credentials: Credentials
    ) = {
    val tx = Tx(
      inputData = "",
      nonce = 0,
      gasLimit = BigInt("210000"),
      gasPrice = BigInt("200000"),
      to = to,
      value = amountStr.zeros(WETH_TOKEN.decimals)
    )
    sendTransaction(tx)
  }

  def transferErc20(
      to: String,
      token: String,
      amount: BigInt
    )(
      implicit
      credentials: Credentials
    ) = {
    val input = erc20Abi.transfer.pack(TransferFunction.Parms(to, amount))
    val tx = Tx(
      inputData = input,
      nonce = 0,
      gasLimit = BigInt("210000"),
      gasPrice = BigInt("200000"),
      to = token,
      value = 0
    )
    sendTransaction(tx)
  }

  def transferWETH(
      to: String,
      amountStr: String
    )(
      implicit
      credentials: Credentials
    ) = {
    transferErc20(to, WETH_TOKEN.address, amountStr.zeros(WETH_TOKEN.decimals))
  }

  def transferLRC(
      to: String,
      amountStr: String
    )(
      implicit
      credentials: Credentials
    ) = {
    transferErc20(to, LRC_TOKEN.address, amountStr.zeros(LRC_TOKEN.decimals))
  }

  def transferGTO(
      to: String,
      amountStr: String
    )(
      implicit
      credentials: Credentials
    ) = {
    transferErc20(to, GTO_TOKEN.address, amountStr.zeros(GTO_TOKEN.decimals))
  }

  def approveErc20(
      spender: String,
      token: String,
      amount: BigInt
    )(
      implicit
      credentials: Credentials
    ) = {
    val input = erc20Abi.approve.pack(ApproveFunction.Parms(spender, amount))
    val tx = Tx(
      inputData = input,
      nonce = 0,
      gasLimit = BigInt("210000"),
      gasPrice = BigInt("200000"),
      to = token,
      value = 0
    )
    sendTransaction(tx)
  }

  def approveWETHToDelegate(
      amountStr: String
    )(
      implicit
      credentials: Credentials
    ) = {
    approveErc20(
      config.getString("loopring_protocol.delegate-address"),
      WETH_TOKEN.address,
      amountStr.zeros(WETH_TOKEN.decimals)
    )
  }

  def approveLRCToDelegate(
      amountStr: String
    )(
      implicit
      credentials: Credentials
    ) = {
    approveErc20(
      config.getString("loopring_protocol.delegate-address"),
      LRC_TOKEN.address,
      amountStr.zeros(LRC_TOKEN.decimals)
    )
  }

  def approveGTOToDelegate(
      amountStr: String
    )(
      implicit
      credentials: Credentials
    ) = {
    approveErc20(
      config.getString("loopring_protocol.delegate-address"),
      GTO_TOKEN.address,
      amountStr.zeros(GTO_TOKEN.decimals)
    )
  }

  def sendTransaction(txWithoutNonce: Tx)(implicit credentials: Credentials) = {
    val getNonceF = (actors.get(EthereumAccessActor.name) ? GetNonce.Req(
      credentials.getAddress,
      "latest"
    ))
    val getNonceRes =
      Await.result(getNonceF.mapTo[GetNonce.Res], timeout.duration)
    val tx = txWithoutNonce.copy(
      nonce = NumericConversion.toBigInt(getNonceRes.result).intValue
    )
    actors.get(EthereumAccessActor.name) ? SendRawTransaction.Req(
      getSignedTxData(tx)
    )
  }

  def cancelOrders(
      orderHashes: Seq[String]
    )(
      implicit
      credentials: Credentials
    ) = {
    val input = orderCancellerAbi.cancelOrders.pack(
      CancelOrdersFunction.Params(
        Numeric.hexStringToByteArray(
          orderHashes.map(Numeric.cleanHexPrefix).mkString("")
        )
      )
    )
    val tx = Tx(
      inputData = input,
      nonce = 0,
      gasLimit = BigInt("210000"),
      gasPrice = BigInt("200000"),
      to = orderCancelAddress,
      value = 0
    )
    sendTransaction(tx)
  }

  def cancelAllOrders(cutoff: BigInt)(implicit credentials: Credentials) = {

    val input = orderCancellerAbi.cancelAllOrders.pack(
      CancelAllOrdersFunction.Params(cutoff)
    )
    val tx = Tx(
      inputData = input,
      nonce = 0,
      gasLimit = BigInt("210000"),
      gasPrice = BigInt("200000"),
      to = orderCancelAddress,
      value = 0
    )
    sendTransaction(tx)
  }

  def cancelAllOrdersByTokenPair(
      cutoff: BigInt,
      token1: String = LRC_TOKEN.address,
      token2: String = WETH_TOKEN.address
    )(
      implicit
      credentials: Credentials
    ) = {
    val input = orderCancellerAbi.cancelAllOrdersForTradingPair.pack(
      CancelAllOrdersForTradingPairFunction.Params(token1, token2, cutoff)
    )
    val tx = Tx(
      inputData = input,
      nonce = 0,
      gasLimit = BigInt("210000"),
      gasPrice = BigInt("200000"),
      to = orderCancelAddress,
      value = 0
    )
    sendTransaction(tx)
  }

  def getUniqueAccountWithoutEth = {
    val account = getUniqueAccount()
    info(
      s"${this.getClass.getSimpleName} got an uniqueAccount: ${account.getAddress()}"
    )
    account
  }
}
