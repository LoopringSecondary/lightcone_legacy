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

  def start(
    )(
      implicit system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      rb: EthereumCallRequestBuilder,
      brb: EthereumBatchCallRequestBuilder
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
    val actors: Lookup[ActorRef],
    val rb: EthereumCallRequestBuilder,
    val brb: EthereumBatchCallRequestBuilder)
    extends ActorWithPathBasedConfig(EthereumQueryActor.name) {

  val LATEST = "latest"

  val delegateAddress =
    config.getString("loopring_protocol.delegate-address")

  val tradeHistoryAddress =
    config.getString("loopring_protocol.trade-history-address")

  protected def ethereumAccessorActor = actors.get(EthereumAccessActor.name)

  def receive = LoggingReceive {
    case req: GetBalanceAndAllowances.Req =>
      val (ethToken, erc20Tokens) = req.tokens.partition(Address(_).isZero)

      val batchReqs = brb
        .buildRequest(Address(delegateAddress), req.copy(tokens = erc20Tokens))

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
          .Res(req.address, (erc20Tokens zip balanceAndAllowance).toMap)

        ethRes <- ethToken match {
          case head :: tail =>
            (ethereumAccessorActor ? EthGetBalance.Req(
              address = Address(req.address).toString,
              tag = LATEST
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

    case req: GetBalance.Req =>
      val (ethToken, erc20Tokens) = req.tokens.partition(Address(_).isZero)
      val batchReqs = brb.buildRequest(req.copy(tokens = erc20Tokens))

      (for {
        batchRes <- (ethereumAccessorActor ? batchReqs)
          .mapAs[BatchCallContracts.Res]

        balances = batchRes.resps.map { res =>
          ByteString.copyFrom(Numeric.toBigInt(res.result).toByteArray)
        }

        result = GetBalance.Res(req.address, (erc20Tokens zip balances).toMap)

        ethRes <- ethToken match {
          case head :: tail =>
            (ethereumAccessorActor ? EthGetBalance.Req(
              address = Address(req.address).toString,
              tag = LATEST
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

    case req: GetAllowance.Req =>
      batchCallEthereum(sender, brb.buildRequest(Address(delegateAddress), req)) {
        result =>
          val allowances = result.map { res =>
            ByteString.copyFrom(Numeric.toBigInt(res).toByteArray)
          }
          GetAllowance.Res(req.address, (req.tokens zip allowances).toMap)
      }

    case req: GetFilledAmount.Req =>
      batchCallEthereum(
        sender,
        brb
          .buildRequest(Address(tradeHistoryAddress), req)
      ) { result =>
        GetFilledAmount.Res(
          (req.orderIds zip result.map(
            res => ByteString.copyFrom(Numeric.hexStringToByteArray(res))
          )).toMap
        )
      }

    case req: GetOrderCancellation.Req =>
      callEthereum(
        sender,
        rb.buildRequest(req, Address(tradeHistoryAddress), LATEST)
      ) { result =>
        GetOrderCancellation.Res(Numeric.toBigInt(result).intValue() == 1)
      }

    case req: GetCutoff.Req =>
      callEthereum(
        sender,
        rb.buildRequest(req, Address(tradeHistoryAddress), LATEST)
      ) { result =>
        GetCutoff.Res(Numeric.toBigInt(result).toByteArray)
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
