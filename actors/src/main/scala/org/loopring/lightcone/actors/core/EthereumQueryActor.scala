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
      actors: Lookup[ActorRef],
      requestBuilder: EthereumCallRequestBuilder,
      batchRequestBuilder: EthereumBatchCallRequestBuilder
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
    val requestBuilder: EthereumCallRequestBuilder,
    val batchRequestBuilder: EthereumBatchCallRequestBuilder)
    extends ActorWithPathBasedConfig(EthereumQueryActor.name) {

  val LATEST = "latest"

  val delegateAddress = config.getString("loopring_protocol.delegate-address")

  val tradeHistoryAddress =
    config.getString("loopring_protocol.trade-history-address")

  protected def ethereumAccessorActor = actors.get(EthereumAccessActor.name)

  //todo:还需要继续优化下
  def receive = LoggingReceive {
    case req: GetBalanceAndAllowances.Req =>
      val erc20Tokens = req.tokens.filterNot(
        token => Address(token).toString.equals(Address.zeroAddress)
      )
      val ethToken =
        req.tokens.find(
          token => Address(token).toString.equals(Address.zeroAddress)
        )

      val batchReqs =
        batchRequestBuilder
          .buildRequest(
            Address(delegateAddress),
            req.copy(tokens = erc20Tokens)
          )
      (for {
        batchRes <- (ethereumAccessorActor ? batchReqs)
          .mapAs[BatchCallContracts.Res]
        ethRes <- ethToken match {
          case Some(_) =>
            (ethereumAccessorActor ? EthGetBalance.Req(
              address = Address(req.address).toString,
              tag = LATEST
            )).mapAs[EthGetBalance.Res].map(Some(_))
          case None => Future.successful(None)
        }
      } yield {
        val allowances = batchRes.resps.filter(_.id % 2 == 0).map { res =>
          Numeric.toBigInt(res.result).toByteArray
        }
        val balances =
          batchRes.resps.filter(_.id % 2 == 1).map { res =>
            Numeric.toBigInt(res.result).toByteArray
          }
        val balanceAndAllowance = (balances zip allowances).map { ba =>
          BalanceAndAllowance(ba._1, ba._2)
        }
        val res = GetBalanceAndAllowances
          .Res(req.address, (erc20Tokens zip balanceAndAllowance).toMap)

        ethRes match {
          case Some(_) =>
            res.copy(
              balanceAndAllowanceMap = res.balanceAndAllowanceMap +
                (ethToken.get → BalanceAndAllowance(
                  BigInt(Numeric.toBigInt(ethRes.get.result)),
                  BigInt(0)
                ))
            )
          case None =>
            res
        }
      }) sendTo sender

    case req: GetBalance.Req =>
      val erc20Tokens = req.tokens.filterNot(
        token => Address(token).toString.equals(Address.zeroAddress)
      )
      val ethToken =
        req.tokens.find(
          token => Address(token).toString.equals(Address.zeroAddress)
        )

      val batchReqs = batchRequestBuilder
        .buildRequest(req.copy(tokens = erc20Tokens))

      (for {
        batchRes <- (ethereumAccessorActor ? batchReqs)
          .mapAs[BatchCallContracts.Res]
        ethRes <- ethToken match {
          case Some(_) =>
            (ethereumAccessorActor ? EthGetBalance.Req(
              address = Address(req.address).toString,
              tag = LATEST
            )).mapAs[EthGetBalance.Res].map(Some(_))
          case None => Future.successful(None)
        }
      } yield {
        val balances = batchRes.resps.map { res =>
          ByteString.copyFrom(Numeric.toBigInt(res.result).toByteArray)
        }
        val res = GetBalance.Res(req.address, (erc20Tokens zip balances).toMap)
        ethRes match {
          case Some(_) =>
            res.copy(
              balanceMap = res.balanceMap +
                (Address.zeroAddress → BigInt(
                  Numeric.toBigInt(ethRes.get.result)
                ))
            )
          case None =>
            res
        }
      }) sendTo sender
    case req: GetAllowance.Req =>
      batchCallEthereum(
        sender,
        batchRequestBuilder
          .buildRequest(Address(delegateAddress), req)
      ) { result =>
        {
          val allowances = result.map { res =>
            ByteString.copyFrom(Numeric.toBigInt(res).toByteArray)
          }
          GetAllowance.Res(req.address, (req.tokens zip allowances).toMap)
        }
      }
    case req: GetFilledAmount.Req =>
      batchCallEthereum(
        sender,
        batchRequestBuilder
          .buildRequest(Address(tradeHistoryAddress), req)
      ) { result =>
        {
          GetFilledAmount.Res(
            (req.orderIds zip result.map(
              res => ByteString.copyFrom(Numeric.hexStringToByteArray(res))
            )).toMap
          )
        }
      }

    case req: GetOrderCancellation.Req =>
      callEthereum(
        sender,
        requestBuilder
          .buildRequest(req, Address(tradeHistoryAddress), LATEST)
      ) { result =>
        GetOrderCancellation.Res(Numeric.toBigInt(result).intValue() == 1)
      }

    case req: GetCutoff.Req =>
      callEthereum(
        sender,
        requestBuilder
          .buildRequest(req, Address(tradeHistoryAddress), LATEST)
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
