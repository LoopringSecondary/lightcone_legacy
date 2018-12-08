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
import akka.event.LoggingReceive
import akka.util.Timeout
import akka.pattern.ask
import com.google.inject.Inject
import com.google.inject.name.Named
import com.google.protobuf.ByteString
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.ethereum.EthereumConnectionActor
import org.web3j.utils.Numeric

import scala.concurrent._

object AccountBalanceActor {
  val name = "account_balance"
}

class AccountBalanceActor @Inject() (
    val actors: Lookup[ActorRef]
)(
    implicit
    ec: ExecutionContext,
    timeout: Timeout,
    @Named("delegate-address") val delegateAddress: String
)
  extends Actor
  with ActorLogging {

  protected def ethereumConnectionActor: ActorRef = actors.get(EthereumConnectionActor.name)

  val erc20ABI = ERC20ABI()
  val zeroAddress: String = "0" * 40

  def receive: Receive = LoggingReceive {
    // TODO(dongw): even if the token is not supported, we still need to return 0s.
    case req: XGetBalanceAndAllowancesReq ⇒
      val tokens = req.tokens.filterNot(token ⇒ Numeric.cleanHexPrefix(token).equals(zeroAddress))
      val ethToken: Option[String] = req.tokens.find(token ⇒ Numeric.cleanHexPrefix(token).equals(zeroAddress))
      val balanceCallReqs = tokens.map(token ⇒ {
        val data = erc20ABI.balanceOf.pack(BalanceOfFunction.Parms(req.address))
        val param = XTransactionParam(to = token, data = data)
        XEthCallReq(Some(param), tag = "latest")
      })
      val batchBalanceReq = XBatchContractCallReq(balanceCallReqs)
      val allowanceCallReqs = tokens.map(token ⇒ {
        val data = erc20ABI.allowance.pack(
          AllowanceFunction.Parms(_spender = delegateAddress, _owner = req.address)
        )
        val param = XTransactionParam(to = token, data = data)
        XEthCallReq(Some(param), tag = "latest")
      })
      val batchAllowanceReq = XBatchContractCallReq(allowanceCallReqs)
      for {
        balances ← (ethereumConnectionActor ? batchBalanceReq)
          .mapTo[XBatchContractCallRes]
          .map(_.resps)
          .map(_.map(res ⇒ BigInt(Numeric.toBigInt(res.result))))
        allowances ← (ethereumConnectionActor ? batchAllowanceReq)
          .mapTo[XBatchContractCallRes]
          .map(_.resps)
          .map(_.map(res ⇒ BigInt(Numeric.toBigInt(res.result))))
        ethBalance ← ethToken match {
          case Some(_) ⇒ (ethereumConnectionActor ? XEthGetBalanceReq(address = req.address, tag = "latest"))
            .mapTo[XEthGetBalanceRes]
            .map(res ⇒ Some(BigInt(Numeric.toBigInt(res.result))))
          case None ⇒
            Future.successful(None)
        }
      } yield {
        val balanceAndAllowance =
          (balances zip allowances)
            .map(ba ⇒ XBalanceAndAllowance(ba._1, ba._2))

        ethBalance match {
          case Some(ether) ⇒
            sender ! XGetBalanceAndAllowancesRes(
              req.address,
              (tokens zip balanceAndAllowance).toMap.+(ethToken.get → XBalanceAndAllowance(ether, BigInt(0)))
            )
          case None ⇒
            sender ! XGetBalanceAndAllowancesRes(
              req.address,
              (tokens zip balanceAndAllowance).toMap
            )
        }
      }
    case req: XGetBalanceReq ⇒
      val tokens = req.tokens.filterNot(token ⇒ Numeric.cleanHexPrefix(token).equals(zeroAddress))
      val ethToken: Option[String] = req.tokens.find(token ⇒ Numeric.cleanHexPrefix(token).equals(zeroAddress))
      val balanceCallReqs = tokens.map(token ⇒ {
        val data = erc20ABI.balanceOf.pack(BalanceOfFunction.Parms(req.address))
        val param = XTransactionParam(to = token, data = data)
        XEthCallReq(Some(param), tag = "latest")
      })
      val batchBalanceReq = XBatchContractCallReq(balanceCallReqs)
      for {
        balances ← (ethereumConnectionActor ? batchBalanceReq)
          .mapTo[XBatchContractCallRes]
          .map(_.resps)
          .map(_.map(res ⇒ ByteString.copyFrom(Numeric.hexStringToByteArray(res.result))))
        ethBalance ← ethToken match {
          case Some(_) ⇒ (ethereumConnectionActor ? XEthGetBalanceReq(address = req.address, tag = "latest"))
            .mapTo[XEthGetBalanceRes]
            .map(res ⇒ Some(BigInt(Numeric.toBigInt(res.result))))
          case None ⇒
            Future.successful(None)
        }
      } yield {
        ethBalance match {
          case Some(ether) ⇒
            sender ! XGetBalanceRes(
              req.address,
              (tokens zip balances).toMap + (ethToken.get → ether)
            )
          case None ⇒
            sender ! XGetBalanceRes(
              req.address,
              (req.tokens zip balances).toMap
            )
        }
      }
    case req: XGetAllowanceReq ⇒
      val tokens = req.tokens.filterNot(token ⇒ Numeric.cleanHexPrefix(token).equals(zeroAddress))
      val ethToken: Option[String] = req.tokens.find(token ⇒ Numeric.cleanHexPrefix(token).equals(zeroAddress))
      val allowanceCallReqs = tokens.map(token ⇒ {
        val data = erc20ABI.allowance.pack(
          AllowanceFunction.Parms(_spender = delegateAddress, _owner = req.address)
        )
        val param = XTransactionParam(to = token, data = data)
        XEthCallReq(Some(param), tag = "latest")
      })
      val batchAllowanceReq = XBatchContractCallReq(allowanceCallReqs)
      for {
        allowances ← (ethereumConnectionActor ? batchAllowanceReq)
          .mapTo[XBatchContractCallRes]
          .map(_.resps)
          .map(_.map(res ⇒ ByteString.copyFrom(Numeric.hexStringToByteArray(res.result))))
      } yield {
        ethToken match {
          case Some(address) ⇒
            sender ! XGetAllowanceRes(
              req.address,
              (req.tokens zip allowances).toMap + (address → BigInt(0))
            )
          case None ⇒
            sender ! XGetAllowanceRes(
              req.address,
              (req.tokens zip allowances).toMap
            )
        }

      }

    case msg ⇒
      log.error(s"unsupported msg send to AccountBalanceActor:${msg}")
  }

}
