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
import org.loopring.lightcone.lib._
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric
import scala.concurrent._

// main owner: 李亚东
object AccountBalanceActor extends EvenlySharded {
  val name = "account_balance"

  def startShardRegion()(implicit
    system: ActorSystem,
    config: Config,
    ec: ExecutionContext,
    timeProvider: TimeProvider,
    timeout: Timeout,
    actors: Lookup[ActorRef]
  ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shareds")
    entitiesPerShard = selfConfig.getInt("entities-per-shard")

    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new AccountBalanceActor()),
      settings = ClusterShardingSettings(system).withRole(name),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
  }
}

class AccountBalanceActor()(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef]
) extends ActorWithPathBasedConfig(AccountBalanceActor.name) {

  val delegateAddress = config.getString("loopring-protocol.delegate-address")
  val erc20Abi = ERC20ABI()
  val zeroAddress: String = "0x" + "0" * 40

  protected def ethereumConnectionActor = actors.get(EthereumAccessActor.name)

  def receive = LoggingReceive {
    case req: XGetBalanceAndAllowancesReq ⇒
      if (!Address.isValid(req.address) || !req.tokens.forall(Address.isValid)) {
        log.error(s"invalid XGetBalanceAndAllowancesReq:$req caused by invalid ethereum address")
        sender ! XGetBalanceAndAllowancesRes()
          .withAddress(req.address)
          .withError(XError(error = s"invalid address in XGetBalanceAndAllowancesReq:$req"))
      } else {
        val tokens = req.tokens.filterNot(token ⇒ Address(token).toString.equals(zeroAddress))
        val ethToken = req.tokens.find(token ⇒ Address(token).toString.equals(zeroAddress))
        val batchBalanceReq = packBatchBalanceCallReq(Address(req.address), tokens.map(Address(_)))
        val batchAllowanceReq = packBatchAllowanceCallReq(Address(req.address), tokens.map(Address(_)))
        (for {
          balances ← (ethereumConnectionActor ? batchBalanceReq)
            .mapTo[XBatchContractCallRes]
            .map(_.resps)
            .map(_.map(res ⇒ BigInt(Numeric.toBigInt(res.result))))
          allowances ← (ethereumConnectionActor ? batchAllowanceReq)
            .mapTo[XBatchContractCallRes]
            .map(_.resps)
            .map(_.map(res ⇒ BigInt(Numeric.toBigInt(res.result))))
          ethBalance ← ethToken match {
            case Some(_) ⇒ (ethereumConnectionActor ?
              XEthGetBalanceReq(address = Address(req.address).toString, tag = "latest"))
              .mapTo[XEthGetBalanceRes]
              .map(res ⇒ Some(BigInt(Numeric.toBigInt(res.result))))
            case None ⇒
              Future.successful(None)
          }
          balanceAndAllowance = (balances zip allowances).map {
            ba ⇒ XBalanceAndAllowance(ba._1, ba._2)
          }
          result = ethBalance match {
            case Some(ether) ⇒
              XGetBalanceAndAllowancesRes(
                req.address,
                (tokens zip balanceAndAllowance).toMap +
                  (ethToken.get → XBalanceAndAllowance(ether, BigInt(0)))
              )
            case None ⇒
              XGetBalanceAndAllowancesRes(
                req.address,
                (tokens zip balanceAndAllowance).toMap
              )
          }
        } yield result) pipeTo sender
      }

    case req: XGetBalanceReq ⇒
      if (!Address.isValid(req.address) || !req.tokens.forall(Address.isValid)) {
        log.error(s"invalid XGetBalanceReq:$req caused by invalid ethereum address")
        sender ! XGetBalanceRes()
          .withAddress(req.address)
          .withError(XError(error = s"invalid address in XGetBalanceReq:$req"))
      } else {
        val tokens = req.tokens.filterNot(token ⇒ Address(token).toString.equals(zeroAddress))
        val ethToken = req.tokens.find(token ⇒ Address(token).toString.equals(zeroAddress))
        val batchBalanceReq = packBatchBalanceCallReq(Address(req.address), tokens.map(Address(_)))
        (for {
          balances ← (ethereumConnectionActor ? batchBalanceReq)
            .mapTo[XBatchContractCallRes]
            .map(_.resps)
            .map(_.map { res ⇒
              ByteString.copyFrom(Numeric.hexStringToByteArray(res.result))
            })
          ethBalance ← ethToken match {
            case Some(_) ⇒ (ethereumConnectionActor ?
              XEthGetBalanceReq(
                address = Address(req.address).toString,
                tag = "latest"
              ))
              .mapTo[XEthGetBalanceRes]
              .map { res ⇒
                Some(BigInt(Numeric.toBigInt(res.result)))
              }
            case None ⇒
              Future.successful(None)
          }
          result = ethBalance match {
            case Some(ether) ⇒
              XGetBalanceRes(
                req.address,
                (tokens zip balances).toMap + (ethToken.get → ether)
              )
            case None ⇒
              XGetBalanceRes(
                req.address,
                (req.tokens zip balances).toMap
              )
          }
        } yield result) pipeTo sender
      }

    case req: XGetAllowanceReq ⇒
      if (!Address.isValid(req.address) || !req.tokens.forall(Address.isValid)) {
        log.error(s"invalid XGetAllowanceReq:$req caused by invalid ethereum address")
        sender ! XGetAllowanceRes()
          .withAddress(req.address)
          .withError(XError(error = s"invalid address in XGetAllowanceReq:$req"))
      } else {
        val tokens = req.tokens.filterNot(token ⇒ Address(token).toString.equals(zeroAddress))
        val ethToken = req.tokens.find(token ⇒ Address(token).toString.equals(zeroAddress))
        val batchAllowanceReq = packBatchAllowanceCallReq(Address(req.address), tokens.map(Address(_)))
        (for {
          allowances ← (ethereumConnectionActor ? batchAllowanceReq)
            .mapTo[XBatchContractCallRes]
            .map(_.resps)
            .map(_.map { res ⇒
              ByteString.copyFrom(Numeric.hexStringToByteArray(res.result))
            })
          result = ethToken match {
            case Some(address) ⇒
              XGetAllowanceRes(
                req.address,
                (req.tokens zip allowances).toMap + (address → BigInt(0))
              )
            case None ⇒
              XGetAllowanceRes(
                req.address,
                (req.tokens zip allowances).toMap
              )
          }
        } yield result) pipeTo sender
      }
  }

  private def packBatchBalanceCallReq(
    owner: Address,
    tokens: Seq[Address],
    tag: String = "latest"
  ): XBatchContractCallReq = {
    val balanceCallReqs = tokens.map(token ⇒
      {
        val data = erc20Abi.balanceOf.pack(
          BalanceOfFunction.Parms(_owner = owner.toString)
        )
        val param = XTransactionParam(to = token.toString, data = data)
        XEthCallReq(Some(param), tag)
      })
    XBatchContractCallReq(balanceCallReqs)
  }

  private def packBatchAllowanceCallReq(
    owner: Address,
    tokens: Seq[Address],
    tag: String = "latest"
  ): XBatchContractCallReq = {
    val allowanceCallReqs = tokens.map(token ⇒ {
      val data = erc20Abi.allowance.pack(
        AllowanceFunction.Parms(
          _spender = Address(delegateAddress).toString,
          _owner = owner.toString
        )
      )
      val param = XTransactionParam(to = token.toString, data = data)
      XEthCallReq(Some(param), tag)
    })
    XBatchContractCallReq(allowanceCallReqs)
  }

}
