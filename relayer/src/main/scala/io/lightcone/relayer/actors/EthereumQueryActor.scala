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

package io.lightcone.relayer.actors

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.relayer.base._
import io.lightcone.relayer.ethereum._
import io.lightcone.lib._
import io.lightcone.relayer.data._
import io.lightcone.core._
import org.web3j.utils.Numeric
import scala.concurrent._

// Owner: Yadong
object EthereumQueryActor extends DeployedAsShardedEvenly {
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
    startSharding(Props(new EthereumQueryActor()))
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

  @inline protected def ethereumAccessorActor =
    actors.get(EthereumAccessActor.name)

  def ready = LoggingReceive {
    case req @ GetBalanceAndAllowances.Req(owner, tokens, tag) =>
      val (ethToken, erc20Tokens) = tokens.partition(Address(_).isZero)
      val batchReqs =
        brb.buildRequest(delegateAddress, req.copy(tokens = erc20Tokens))
      (for {
        batchRes <- (ethereumAccessorActor ? batchReqs)
          .mapAs[BatchCallContracts.Res]
        (allowanceResps, balanceResps) = batchRes.resps.partition(_.id % 2 == 0)

        allowances = allowanceResps.map { res =>
          NumericConversion.toBigInt(res.result)
        }
        balances = balanceResps.map { res =>
          NumericConversion.toBigInt(res.result)
        }
        balanceAndAllowance = (balances zip allowances).map { ba =>
          BalanceAndAllowance(ba._1, ba._2)
        }

        blockNumber: Long = ??? // TODO(yadong): implement this.
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
                NumericConversion.toBigInt(ethRes.get.result),
                BigInt(0)
              ))
          )
        } else {
          result
        }
      } yield finalResult) sendTo sender

    case req @ GetBalance.Req(owner, tokens, tag) =>
      val (ethToken, erc20Tokens) = tokens.partition(Address(_).isZero)
      val batchReqs = brb.buildRequest(req.copy(tokens = erc20Tokens))
      (for {
        batchRes <- (ethereumAccessorActor ? batchReqs)
          .mapAs[BatchCallContracts.Res]

        balances = batchRes.resps.map { res =>
          bigInt2ByteString(BigInt(Numeric.toBigInt(res.result)))
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
              (Address.ZERO.toString ->
                NumericConversion.toBigInt(ethRes.get.result))
          )
        } else {
          result
        }
      } yield finalResult) sendTo sender

    case req @ GetAllowance.Req(owner, tokens, _) =>
      batchCallEthereum(sender, brb.buildRequest(delegateAddress, req)) {
        result =>
          val allowances = result.map { res =>
            bigInt2ByteString(NumericConversion.toBigInt(res))
          }
          GetAllowance.Res(owner, (tokens zip allowances).toMap)
      }

    // TODOO(yadong): return the block number
    case req @ GetFilledAmount.Req(orderIds, _) =>
      batchCallEthereum(
        sender,
        brb
          .buildRequest(tradeHistoryAddress, req)
      ) { result =>
        GetFilledAmount.Res(
          (orderIds zip result
            .map(res => bigInt2ByteString(NumericConversion.toBigInt(res)))).toMap
        )
      }

    case req: GetOrderCancellation.Req =>
      callEthereum(sender, rb.buildRequest(req, tradeHistoryAddress)) {
        result =>
          GetOrderCancellation.Res(
            NumericConversion.toBigInt(result).intValue == 1
          )
      }

    case req: GetCutoff.Req =>
      callEthereum(sender, rb.buildRequest(req, tradeHistoryAddress)) {
        result =>
          GetCutoff.Res(
            req.broker,
            req.owner,
            req.marketHash,
            NumericConversion.toBigInt(result)
          )
      }
    case req: BatchGetCutoffs.Req =>
      batchCallEthereum(sender, brb.buildRequest(req, tradeHistoryAddress)) {
        result =>
          BatchGetCutoffs.Res((req.reqs zip result).map {
            case (cutoffReq, res) =>
              GetCutoff.Res(
                cutoffReq.broker,
                cutoffReq.owner,
                cutoffReq.marketHash,
                NumericConversion.toBigInt(res)
              )
          })
      }

    case req: GetBurnRate.Req =>
      callEthereum(sender, rb.buildRequest(req, burnRateTableAddress)) {
        result =>
          {
            val formatResult = Numeric.cleanHexPrefix(result)
            if (formatResult.length == 64) {
              val p2pRate = NumericConversion
                .toBigInt(formatResult.substring(56, 60))
                .doubleValue() / base
              val marketRate = NumericConversion
                .toBigInt(formatResult.substring(60))
                .doubleValue() / base
              GetBurnRate.Res(forMarket = marketRate, forP2P = p2pRate)
            } else {
              throw ErrorException(
                ErrorCode.ERR_UNEXPECTED_RESPONSE,
                "unexpected response"
              )
            }
          }
      }
    case req @ Notify("echo", _) =>
      sender ! req
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
