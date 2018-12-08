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

import java.math.BigInteger

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import akka.util.Timeout
import akka.pattern.ask
import com.google.protobuf.ByteString
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.ethereum.abi.{AllowanceFunction, BalanceOfFunction, ERC20ABI}
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.ethereum.EthereumConnectionActor
import org.web3j.utils.Numeric

import scala.concurrent._

object AccountBalanceActor {
  val name = "account_balance"
}

// TODO(fukun): implement this class.
class AccountBalanceActor(
    val actors: Lookup[ActorRef]
)(
    implicit
    ec: ExecutionContext,
    timeout: Timeout
)
  extends Actor
  with ActorLogging {

  protected def ethereumConnectionActor: ActorRef = actors.get(EthereumConnectionActor.name)

  val erc20ABI = ERC20ABI()

  def receive: Receive = LoggingReceive {
    // TODO(dongw): even if the token is not supported, we still need to return 0s.
    //TODO(yadong) 如果是ETH这里token如何表示，如何做特殊的处理
    case req: XGetBalanceAndAllowancesReq ⇒
      val balanceCallReqs = req.tokens.map(token ⇒ {
        val data = erc20ABI.balanceOf.pack(BalanceOfFunction.Parms(req.address))
        val param = XTransactionParam(to = token, data = data)
        XEthCallReq(Some(param), tag = "latest")
      })
      val batchBalanceReq = XBatchContractCallReq(balanceCallReqs)
      val allowanceCallReqs = req.tokens.map(token ⇒ {
        //TODO(yadong) 授权地址暂时写死，等后续定获取方式修改
        val data = erc20ABI.allowance.pack(
          AllowanceFunction.Parms(_spender = "0x17233e07c67d086464fD408148c3ABB56245FA64", _owner = req.address)
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
      } yield {
        val balanceAndAllowance =
          (balances zip allowances)
            .map(ba ⇒ XBalanceAndAllowance(ba._1, ba._2))

        sender ! XGetBalanceAndAllowancesRes(
          req.address,
          (req.tokens zip balanceAndAllowance).toMap
        )
      }
    case req:XGetBalanceReq ⇒
      val balanceCallReqs = req.tokens.map(token ⇒ {
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
      } yield {
        sender ! XGetBalanceRes(
          req.address,
          (req.tokens zip balances).toMap
        )
      }
    case req:XGetAllowanceReq ⇒
      val allowanceCallReqs = req.tokens.map(token ⇒ {
        //TODO(yadong) 授权地址暂时写死，等后续定获取方式修改
        val data = erc20ABI.allowance.pack(
          AllowanceFunction.Parms(_spender = "0x17233e07c67d086464fD408148c3ABB56245FA64", _owner = req.address)
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
        sender ! XGetAllowanceRes(
          req.address,
          (req.tokens zip allowances).toMap
        )
      }

    case msg ⇒
      log.error(s"unsupported msg send to AccountBalanceActor:${msg}")
  }

}
