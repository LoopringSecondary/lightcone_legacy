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

package org.loopring.lightcone.actors.core

import akka.actor._
import akka.cluster.sharding._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.google.protobuf.ByteString
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.ethereum.EthereumAccessActor
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.TimeProvider
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.ethereum._

import scala.concurrent.{ExecutionContext, Future}

object EthereumQueryActor extends ShardedEvenly {
  val name = "ethereum_query"

  def startShardRegion(
    )(
      implicit system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef]
    ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shards")
    entitiesPerShard = selfConfig.getInt("entities-per-shard")

    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new EthereumQueryActor()),
      settings = ClusterShardingSettings(system).withRole(name),
      messageExtractor = messageExtractor
    )
  }
}

class EthereumQueryActor(
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef])
    extends ActorWithPathBasedConfig(EthereumQueryActor.name) {

  val erc20Abi = ERC20ABI()

  val delegateAddress = config.getString("loopring_protocol.delegate-address")

  val tradeHistoryAddress =
    config.getString("loopring_protocol.trade-history-address")
  val zeroAddress: String = "0x" + "0" * 40

  protected def ethereumAccessorActor = actors.get(EthereumAccessActor.name)

  //todo:还需要继续优化下
  def receive = LoggingReceive {
    case req: GetBalanceAndAllowances.Req =>
      val erc20Tokens = req.tokens.filterNot(
        token ⇒ Address(token).toString.equals(zeroAddress)
      )
      val ethToken =
        req.tokens.find(token ⇒ Address(token).toString.equals(zeroAddress))

      (for {
        batchReqs ← Future {
          getBalanceAndAllowanceToBatchReq(
            Address(delegateAddress),
            req.copy(tokens = erc20Tokens)
          )
        }
        callRes <- (ethereumAccessorActor ? batchReqs)
          .mapAs[BatchCallContracts.Res]
        ethRes <- ethToken match {
          case Some(_) ⇒
            (ethereumAccessorActor ? EthGetBalance.Req(
              address = Address(req.address).toString,
              tag = "latest"
            )).mapAs[EthGetBalance.Res].map(Some(_))
          case None => Future.successful(None)
        }
        res: GetBalanceAndAllowances.Res = batchContractCallResToBalanceAndAllowance(
          req.address,
          erc20Tokens,
          callRes
        )
      } yield {
        ethRes match {
          case Some(_) ⇒
            res.copy(
              balanceAndAllowanceMap = res.balanceAndAllowanceMap +
                (ethToken.get → BalanceAndAllowance(
                  BigInt(Numeric.toBigInt(ethRes.get.result)),
                  BigInt(0)
                ))
            )
          case None ⇒
            res
        }
      }) sendTo sender

    case req: GetBalance.Req =>
      val erc20Tokens = req.tokens.filterNot(
        token ⇒ Address(token).toString.equals(zeroAddress)
      )
      val ethToken =
        req.tokens.find(token ⇒ Address(token).toString.equals(zeroAddress))

      (for {
        batchReqs ← Future { req.copy(tokens = erc20Tokens) }
        callRes ← (ethereumAccessorActor ? batchReqs)
          .mapAs[BatchCallContracts.Res]
        callRes <- (ethereumAccessorActor ? batchReqs)
          .mapAs[BatchCallContracts.Res]
        ethRes <- ethToken match {
          case Some(_) ⇒
            (ethereumAccessorActor ? EthGetBalance.Req(
              address = Address(req.address).toString,
              tag = "latest"
            )).mapAs[EthGetBalance.Res].map(Some(_))
          case None ⇒ Future.successful(None)
        }
        res: GetBalance.Res = batchContractCallResToBalance(
          req.address,
          req.tokens,
          callRes
        )
      } yield {
        ethRes match {
          case Some(_) ⇒
            res.copy(
              balanceMap = res.balanceMap +
                (zeroAddress → BigInt(Numeric.toBigInt(ethRes.get.result)))
            )
          case None ⇒
            res
        }
      }) sendTo sender
    // 查询授权不应该有ETH的授权
    case req: GetAllowance.Req =>
      (for {
        batchReqs: BatchCallContracts.Req ← Future {
          getAllowanceToBatchReq(Address(delegateAddress), req)
        }
        callRes <- (ethereumAccessorActor ? batchReqs)
          .mapAs[BatchCallContracts.Res]
        res: GetAllowance.Res = batchContractCallResToAllowance(
          req.address,
          req.tokens,
          callRes
        )
      } yield res) sendTo sender

    case req: GetFilledAmount.Req ⇒
      (for {
        batchReq ← Future {
          getFilledAmountToBatchReq(Address(tradeHistoryAddress), req)
        }
        batchRes <- (ethereumAccessorActor ? batchReq)
          .mapAs[BatchCallContracts.Res]
          .map(_.resps.map(_.result))
      } yield {
        GetFilledAmount.Res(
          (req.orderIds zip batchRes.map(
            res ⇒ ByteString.copyFrom(Numeric.hexStringToByteArray(res))
          )).toMap
        )
      }) sendTo sender
  }

}
