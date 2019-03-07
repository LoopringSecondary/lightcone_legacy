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

  @inline def ethereumAccessorActor = actors.get(EthereumAccessActor.name)

  def ready = LoggingReceive {
    case req @ GetAccount.Req(owner, tokens, tag) =>
      val (ethToken, erc20Tokens) = tokens.partition(Address(_).isZero)
      val batchReqs =
        brb.buildRequest(delegateAddress, req.copy(tokens = erc20Tokens))
      (for {
        batchRes <- (ethereumAccessorActor ? batchReqs)
          .mapAs[BatchCallContracts.Res]
        (allowanceResps, balanceResps) = batchRes.resps.partition(_.id % 2 == 0)
        allowances = allowanceResps.map { res =>
          Amount(NumericConversion.toBigInt(res.result))
        }
        balances = balanceResps.map { res =>
          Amount(NumericConversion.toBigInt(res.result))
        }
        tokenBalances = erc20Tokens.zipWithIndex.map { token =>
          token._1 -> AccountBalance
            .TokenBalance(
              token._1,
              Some(balances(token._2)),
              Some(allowances(token._2)),
              None,
              None,
              batchRes.block
            )
        }.toMap

        accountBalance = AccountBalance(owner, tokenBalances)

        ethRes <- ethToken match {
          case head :: tail =>
            (ethereumAccessorActor ? BatchGetEthBalance.Req(
              Seq(EthGetBalance.Req(address = Address.normalize(owner), tag)),
              returnBlockNum = brb.shouldReturnBlockNumber(tag)
            )).mapAs[BatchGetEthBalance.Res].map(Some(_))
          case Nil => Future.successful(None)
        }

        // TODO(yadong): we only need to return block number once using the `block` field.
        finalBalance = if (ethRes.isDefined) {
          accountBalance.copy(
            tokenBalanceMap = accountBalance.tokenBalanceMap +
              (ethToken.head -> AccountBalance.TokenBalance(
                Address.ZERO.toString(), // TODO(yadong): do we need this?
                Some(
                  Amount(
                    NumericConversion.toBigInt(ethRes.get.resps.head.result),
                    ethRes.get.block
                  )
                ),
                Some(Amount(BigInt(0))),
                None,
                None,
                ethRes.get.block
              ))
          )
        } else {
          accountBalance
        }
      } yield GetAccount.Res(Some(finalBalance))) sendTo sender

    case req @ GetFilledAmount.Req(orderIds, _) =>
      batchCallEthereum(
        sender,
        brb
          .buildRequest(tradeHistoryAddress, req)
      ) { result =>
        val fills = (orderIds zip result.resps
          .map(
            res => Amount(NumericConversion.toBigInt(res.result), result.block)
          )).toMap
        GetFilledAmount.Res(fills)
      }

    case req: GetOrderCancellation.Req =>
      batchCallEthereum(sender, rb.buildRequest(req, tradeHistoryAddress)) {
        result =>
          GetOrderCancellation.Res(
            NumericConversion.toBigInt(result.resps.head.result).intValue == 1,
            result.block
          )
      }

    case req: GetCutoff.Req =>
      batchCallEthereum(sender, rb.buildRequest(req, tradeHistoryAddress)) {
        result =>
          GetCutoff.Res(
            req.broker,
            req.owner,
            req.marketHash,
            cutoff = Some(
              Amount(
                NumericConversion.toBigInt(result.resps.head.result),
                result.block
              )
            )
          )
      }
    case req: BatchGetCutoffs.Req =>
      batchCallEthereum(sender, brb.buildRequest(req, tradeHistoryAddress)) {
        result =>
          BatchGetCutoffs.Res((req.reqs zip result.resps).map {
            case (cutoffReq, res) =>
              val cutoff = Amount(
                NumericConversion.toBigInt(res.result),
                block = result.block
              )
              GetCutoff.Res(
                cutoffReq.broker,
                cutoffReq.owner,
                cutoffReq.marketHash,
                Some(cutoff)
              )
          })
      }

    case req: GetBurnRate.Req =>
      batchCallEthereum(sender, rb.buildRequest(req, burnRateTableAddress)) {
        result =>
          {
            val formatResult = Numeric.cleanHexPrefix(result.resps.head.result)
            if (formatResult.length == 64) {
              val p2pRate = NumericConversion
                .toBigInt(formatResult.substring(56, 60))
                .doubleValue() / base
              val marketRate = NumericConversion
                .toBigInt(formatResult.substring(60))
                .doubleValue() / base
              GetBurnRate.Res(
                burnRate = Some(BurnRate(marketRate, p2pRate)),
                block = result.block
              )
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
    )(resp: BatchCallContracts.Res => AnyRef
    ) = {
    (ethereumAccessorActor ? batchReq)
      .mapAs[BatchCallContracts.Res]
      .map(resp(_))
      .sendTo(sender)
  }
}
