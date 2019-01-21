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
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.TimeProvider
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.ethereum._
import scala.concurrent.{ExecutionContext, Future}

// Owner: Yadong
object EthereumQueryActor extends ShardedEvenly {
  val name = "ethereum_query"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      rb: EthereumCallRequestBuilder,
      brb: EthereumBatchCallRequestBuilder,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shards")
    entitiesPerShard = selfConfig.getInt("entities-per-shard")

    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new EthereumQueryActor()),
      settings = ClusterShardingSettings(system).withRole(roleOpt),
      messageExtractor = messageExtractor
    )
  }
}

class EthereumQueryActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val rb: EthereumCallRequestBuilder,
    val brb: EthereumBatchCallRequestBuilder)
    extends InitializationRetryActor {

  val loopringConfig = config.getConfig("loopring_protocol")

  val delegateAddress =
    Address(loopringConfig.getString("delegate-address"))

  val tradeHistoryAddress =
    Address(loopringConfig.getString("trade-history-address"))

  val burnRateTableAddress =
    Address(loopringConfig.getString("burnrate-table-address"))

  val base = loopringConfig.getInt("burn-rate-table.base")

  protected def ethereumAccessorActor = actors.get(EthereumAccessActor.name)

  def ready = LoggingReceive {
    case req @ GetBalanceAndAllowances.Req(owner, tokens, tag) =>
      val (ethToken, erc20Tokens) = tokens.partition(Address(_).isZero)
      val batchReqs =
        brb.buildRequest(delegateAddress, req.copy(tokens = erc20Tokens), tag)
      (for {
        batchRes <- (ethereumAccessorActor ? batchReqs)
          .mapAs[BatchCallContracts.Res]
        (allowanceResps, balanceResps) = batchRes.resps.partition(_.id % 2 == 0)

        allowances = allowanceResps.map { res =>
          Numeric.toBigInt(res.result).toByteArray
        }
        balances = balanceResps.map { res =>
          Numeric.toBigInt(res.result).toByteArray
        }
        balanceAndAllowance = (balances zip allowances).map { ba =>
          BalanceAndAllowance(ba._1, ba._2)
        }
        result = GetBalanceAndAllowances
          .Res(owner, (erc20Tokens zip balanceAndAllowance).toMap)

        ethRes <- ethToken match {
          case head :: tail =>
            (ethereumAccessorActor ? EthGetBalance.Req(
              address = Address(owner).toString,
              tag
            )).mapAs[EthGetBalance.Res].map(Some(_))
          case Nil => Future.successful(None)
        }

        finalResult = if (ethRes.isDefined) {
          result.copy(
            balanceAndAllowanceMap = result.balanceAndAllowanceMap +
              (ethToken.head -> BalanceAndAllowance(
                BigInt(Numeric.toBigInt(ethRes.get.result)),
                BigInt(0)
              ))
          )
        } else {
          result
        }
      } yield finalResult) sendTo sender

    case req @ GetBalance.Req(owner, tokens, tag) =>
      val (ethToken, erc20Tokens) = tokens.partition(Address(_).isZero)
      val batchReqs = brb.buildRequest(req.copy(tokens = erc20Tokens), tag)

      (for {
        batchRes <- (ethereumAccessorActor ? batchReqs)
          .mapAs[BatchCallContracts.Res]

        balances = batchRes.resps.map { res =>
          ByteString.copyFrom(Numeric.toBigInt(res.result).toByteArray)
        }

        result = GetBalance.Res(owner, (erc20Tokens zip balances).toMap)

        ethRes <- ethToken match {
          case head :: tail =>
            (ethereumAccessorActor ? EthGetBalance.Req(
              address = Address(owner).toString,
              tag
            )).mapAs[EthGetBalance.Res].map(Some(_))
          case Nil => Future.successful(None)
        }

        finalResult = if (ethRes.isDefined) {
          result.copy(
            balanceMap = result.balanceMap +
              (Address.ZERO.toString -> BigInt(
                Numeric.toBigInt(ethRes.get.result)
              ))
          )
        } else {
          result
        }
      } yield finalResult) sendTo sender

    case req @ GetAllowance.Req(owner, tokens, tag) =>
      batchCallEthereum(sender, brb.buildRequest(delegateAddress, req, tag)) {
        result =>
          val allowances = result.map { res =>
            bigInt2ByteString(BigInt(Numeric.toBigInt(res)))
          }
          GetAllowance.Res(owner, (tokens zip allowances).toMap)
      }

    case req @ GetFilledAmount.Req(orderIds, tag) =>
      batchCallEthereum(
        sender,
        brb
          .buildRequest(tradeHistoryAddress, req, tag)
      ) { result =>
        GetFilledAmount.Res(
          (orderIds zip result.map(
            res => bigInt2ByteString(BigInt(Numeric.toBigInt(res)))
          )).toMap
        )
      }

    case req: GetOrderCancellation.Req =>
      callEthereum(sender, rb.buildRequest(req, tradeHistoryAddress, req.tag)) {
        result =>
          GetOrderCancellation.Res(Numeric.toBigInt(result).intValue() == 1)
      }

    case req: GetCutoff.Req =>
      callEthereum(sender, rb.buildRequest(req, tradeHistoryAddress, req.tag)) {
        result =>
          GetCutoff.Res(Numeric.toBigInt(result).toByteArray)
      }

    case req: GetBurnRate.Req =>
      callEthereum(sender, rb.buildRequest(req, burnRateTableAddress, req.tag)) {
        result =>
          {
            val formatResult = Numeric.cleanHexPrefix(result)
            val p2pRate = Numeric
              .toBigInt(formatResult.substring(56, 60))
              .doubleValue() / base
            val marketRate = Numeric
              .toBigInt(formatResult.substring(60))
              .doubleValue() / base
            GetBurnRate.Res(forMarket = marketRate, forP2P = p2pRate)
          }
      }
  }

  private def callEthereum(
      sender: ActorRef,
      req: AnyRef
    )(resp: String => AnyRef
    ) = {
    (ethereumAccessorActor ? req)
      .mapAs[EthCall.Res]
      .map(_.result)
      .map(resp(_))
      .sendTo(sender)
  }

  private def batchCallEthereum(
      sender: ActorRef,
      batchReq: AnyRef
    )(resp: Seq[String] => AnyRef
    ) = {
    (ethereumAccessorActor ? batchReq)
      .mapAs[BatchCallContracts.Res]
      .map(_.resps.map(_.result))
      .map(resp(_))
      .sendTo(sender)
  }
}
