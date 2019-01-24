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

package org.loopring.lightcone.actors.support

import java.util.concurrent.TimeUnit

import akka.actor.Props
import akka.pattern._
import com.typesafe.config.ConfigFactory
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.actors.validator._
import org.loopring.lightcone.ethereum.data.Transaction
import org.loopring.lightcone.ethereum.ethereum.getSignedTxData
import org.loopring.lightcone.proto._
import org.rnorth.ducttape.TimeoutException
import org.rnorth.ducttape.unreliables.Unreliables
import org.testcontainers.containers.ContainerLaunchException
import org.web3j.crypto.Credentials
import org.web3j.utils.Numeric

import scala.collection.JavaConverters._
import scala.concurrent.Await

trait EthereumSupport {
  my: CommonSpec =>

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
      EthereumQueryMessageValidator.name))

  if (!actors.contains(GasPriceActor.name)) {
    actors.add(GasPriceActor.name, GasPriceActor.start)
  }

  val poolSize =
    config.getConfig(EthereumClientMonitor.name).getInt("pool-size")

  val nodesConfig = ConfigFactory
    .parseString(ethNodesConfigStr)
    .getConfigList("nodes")
    .asScala
    .map { c =>
      EthereumProxySettings
        .Node(host = c.getString("host"), port = c.getInt("port"))
    }

  val connectionPools = (nodesConfig.zipWithIndex.map {
    case (node, index) =>
      val nodeName = s"http_connector_$index"
      val props =
        Props(new HttpConnector(node))
      val actor = system.actorOf(props, nodeName)
      actors.add(nodeName, actor)
      actor
  }).toSeq

  Thread.sleep(1000)

  val blockNumJsonRpcReq = JsonRpc.Request(
    "{\"jsonrpc\":\"2.0\",\"method\":\"eth_blockNumber\",\"params\":[],\"id\":64}")

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
        "Timed out waiting for connectionPools init.)")
  }

  actors.add(EthereumClientMonitor.name, EthereumClientMonitor.start)
  Thread.sleep(1000)
  actors.add(EthereumAccessActor.name, EthereumAccessActor.start)

  def transferEth(
    to: String,
    amountStr: String)(
      implicit credentials: Credentials) = {
    val tx = Transaction(
      inputData = "",
      nonce = 0,
      gasLimit = BigInt("210000"),
      gasPrice = BigInt("200000"),
      to = to,
      value = amountStr.zeros(WETH_TOKEN.decimals))
    sendTransaction(tx)
  }

  def transferErc20(
    to: String,
    token: String,
    amount: BigInt)(
      implicit credentials: Credentials) = {
    val input = erc20Abi.transfer.pack(TransferFunction.Parms(to, amount))
    val tx = Transaction(
      inputData = input,
      nonce = 0,
      gasLimit = BigInt("210000"),
      gasPrice = BigInt("200000"),
      to = token,
      value = 0)
    sendTransaction(tx)
  }

  def transferWETH(
    to: String,
    amountStr: String)(
      implicit credentials: Credentials) = {
    transferErc20(to, WETH_TOKEN.address, amountStr.zeros(WETH_TOKEN.decimals))
  }

  def transferLRC(
    to: String,
    amountStr: String)(
      implicit credentials: Credentials) = {
    transferErc20(to, LRC_TOKEN.address, amountStr.zeros(LRC_TOKEN.decimals))
  }

  def transferGTO(
    to: String,
    amountStr: String)(
      implicit credentials: Credentials) = {
    transferErc20(to, GTO_TOKEN.address, amountStr.zeros(GTO_TOKEN.decimals))
  }

  def approveErc20(
    spender: String,
    token: String,
    amount: BigInt)(
      implicit credentials: Credentials) = {
    val input = erc20Abi.approve.pack(ApproveFunction.Parms(spender, amount))
    val tx = Transaction(
      inputData = input,
      nonce = 0,
      gasLimit = BigInt("210000"),
      gasPrice = BigInt("200000"),
      to = token,
      value = 0)
    sendTransaction(tx)
  }

  def approveWETHToDelegate(
    amountStr: String)(
      implicit credentials: Credentials) = {
    approveErc20(
      config.getString("loopring_protocol.delegate-address"),
      WETH_TOKEN.address,
      amountStr.zeros(WETH_TOKEN.decimals))
  }

  def approveLRCToDelegate(
    amountStr: String)(
      implicit credentials: Credentials) = {
    approveErc20(
      config.getString("loopring_protocol.delegate-address"),
      LRC_TOKEN.address,
      amountStr.zeros(LRC_TOKEN.decimals))
  }

  def approveGTOToDelegate(
    amountStr: String)(
      implicit credentials: Credentials) = {
    approveErc20(
      config.getString("loopring_protocol.delegate-address"),
      GTO_TOKEN.address,
      amountStr.zeros(GTO_TOKEN.decimals))
  }

  def sendTransaction(
    txWithoutNonce: Transaction)(
      implicit credentials: Credentials) = {
    val getNonceF = (actors.get(EthereumAccessActor.name) ? GetNonce.Req(
      credentials.getAddress,
      "latest"))
    val getNonceRes =
      Await.result(getNonceF.mapTo[GetNonce.Res], timeout.duration)
    val tx = txWithoutNonce.copy(
      nonce = Numeric.toBigInt(getNonceRes.result).intValue())
    actors.get(EthereumAccessActor.name) ? SendRawTransaction.Req(
      getSignedTxData(tx))
  }

  def cancelOrders(
    orderHashes: Seq[String])(
      implicit credentials: Credentials) = {
    val input = orderCancellerAbi.cancelOrders.pack(
      CancelOrdersFunction.Params(
        Numeric.hexStringToByteArray(
          orderHashes.map(Numeric.cleanHexPrefix).mkString(""))))
    val tx = Transaction(
      inputData = input,
      nonce = 0,
      gasLimit = BigInt("210000"),
      gasPrice = BigInt("200000"),
      to = orderCancelAddress,
      value = 0)
    sendTransaction(tx)
  }

  def cancelAllOrders(cutoff: BigInt)(implicit credentials: Credentials) = {

    val input = orderCancellerAbi.cancelAllOrders.pack(
      CancelAllOrdersFunction.Params(cutoff))
    val tx = Transaction(
      inputData = input,
      nonce = 0,
      gasLimit = BigInt("210000"),
      gasPrice = BigInt("200000"),
      to = orderCancelAddress,
      value = 0)
    sendTransaction(tx)
  }

  def cancelAllOrdersByMarketKey(
    cutoff: BigInt,
    token1: String = LRC_TOKEN.address,
    token2: String = WETH_TOKEN.address)(
      implicit credentials: Credentials) = {
    val input = orderCancellerAbi.cancelAllOrdersForMarketKey.pack(
      CancelAllOrdersForMarketKeyFunction.Params(token1, token2, cutoff))
    val tx = Transaction(
      inputData = input,
      nonce = 0,
      gasLimit = BigInt("210000"),
      gasPrice = BigInt("200000"),
      to = orderCancelAddress,
      value = 0)
    sendTransaction(tx)
  }

  def getUniqueAccountWithoutEth = {
    Credentials.create(
      Numeric.toHexStringWithPrefix(
        BigInt(addressGenerator.getAndIncrement()).bigInteger))
  }
}
