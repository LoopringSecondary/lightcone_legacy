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

package org.loopring.lightcone.actors.ethereum

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import com.google.inject.Inject
import com.google.inject.name.Named
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.actors.core.AccountManagerActor
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.persistence.dals._
import org.loopring.lightcone.proto.actors._

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent._
import scala.util.{ Failure, Success }

class EthereumDataIndexer(
    val actors: Lookup[ActorRef],
    val delegateAddress: String,
    val blockDal: BlockDal
)(implicit
    ec: ExecutionContext,
    timeout: Timeout
)
  extends Actor with ActorLogging {

  def ethereumConnectionActor: ActorRef = actors.get(EthereumConnectionActor.name)
  def accountManager: ActorRef = actors.get(AccountManagerActor.name)

  val erc20Abi = ERC20ABI()
  val wethAbi = WETHABI()
  val zeroAdd: String = "0x" + "0" * 40

  var currentBlockNumber: BigInt = BigInt(-1)
  var currentBlockHash: String = _

  override def preStart(): Unit = {
    for {
      maxBlock: Option[Long] ← blockDal.findMaxHeight()
      blockNum ← maxBlock match {
        case Some(_) ⇒ Future.successful(BigInt(maxBlock.get))
        case _ ⇒ (ethereumConnectionActor ? XEthBlockNumberReq())
          .mapTo[XEthBlockNumberRes]
          .map(res ⇒ BigInt(res.result))
      }
      blockHash ← (ethereumConnectionActor ? XGetBlockWithTxHashByNumberReq(bigInt2Hex(blockNum)))
        .mapTo[XGetBlockWithTxHashByNumberRes]
        .map(_.result.get.hash)
    } yield {
      currentBlockNumber = blockNum
      currentBlockHash = blockHash
    }
  }

  override def receive: Receive = {
    case _: XStart ⇒ process()

  }
  def process(): Unit = {
    for {
      taskNum ← (ethereumConnectionActor ? XEthBlockNumberReq())
        .mapTo[XEthBlockNumberRes]
        .map(_.result)
      block ← if (taskNum > currentBlockNumber)
        (ethereumConnectionActor ? XGetBlockWithTxObjectByNumberReq(currentBlockNumber + 1))
          .mapTo[XGetBlockWithTxObjectByNumberRes]
      else {
        Future.successful()
      }
      handledTask ← block match {
        case XGetBlockWithTxObjectByNumberRes(_, _, Some(result), _) ⇒
          if (result.parentHash.equals(currentBlockHash))
            indexBlock(result)
          else
            handleFork(currentBlockNumber - 1)
        case _ ⇒
          Future.successful(currentBlockNumber, currentBlockHash)
      }
    } yield {
      if (currentBlockHash.equals(handledTask._2)) {
        context.system.scheduler.scheduleOnce(15 seconds, self, XStart())
      } else {
        currentBlockNumber = handledTask._1
        currentBlockHash = handledTask._2
        self ! XStart()
      }
    }
  }

  // find the fork height
  def handleFork(blockNum: BigInt): Future[(BigInt, String)] = {
    Future.successful((BigInt(0), ""))
  }

  // index block
  def indexBlock(block: XBlockWithTxObject): Future[(BigInt, String)] = {
    for {
      txReceipts ← getAllTxReceipts(block.transactions.map(_.hash))
      balanceAddresses = ListBuffer.empty[(String, String)]
      allowanceAddresses = ListBuffer.empty[(String, String)]
      _ = txReceipts.foreach(receipt ⇒ {
        balanceAddresses.append(receipt.from → zeroAdd)
        receipt.logs.foreach(log ⇒ {
          wethAbi.unpackEvent(log.data, log.topics.toArray) match {
            case Some(transfer: TransferEvent.Result) ⇒
              balanceAddresses.append(transfer.sender → log.address, transfer.receiver → log.address)
            case Some(approval: ApprovalEvent.Result) ⇒
              if (approval.spender.equalsIgnoreCase(delegateAddress))
                allowanceAddresses.append(approval.owner → log.address)
            case Some(deposit: DepositEvent.Result) ⇒
              balanceAddresses.append(deposit.dst → log.address)
            case Some(withdrawal: WithdrawalEvent.Result) ⇒
              balanceAddresses.append(withdrawal.src → log.address)
            case _ ⇒
          }
        })
      })
      balanceReqs = balanceAddresses.unzip._1.toSet.map((add: String) ⇒ {
        XGetBalanceReq(add, balanceAddresses.find(_._1.equalsIgnoreCase(add)).unzip._2.toSet.toSeq)
      })
      allowanceReqs = allowanceAddresses.unzip._1.toSet.map((add: String) ⇒ {
        XGetAllowanceReq(add, allowanceAddresses.find(_._1.equalsIgnoreCase(add)).unzip._2.toSet.toSeq)
      })
      balanceRes ← Future.sequence(balanceReqs.map(req ⇒
        (ethereumConnectionActor ? req).mapTo[XGetBalanceRes]))
      allowanceRes ← Future.sequence(allowanceReqs.map(req ⇒
        (ethereumConnectionActor ? req).mapTo[XGetAllowanceRes]))
    } yield {
      balanceRes.foreach(res ⇒ {
        res.balanceMap.foreach(item ⇒
          accountManager ! XAddressBalanceUpdated(res.address, token = item._1, balance = item._2))
      })
      allowanceRes.foreach(res ⇒ {
        res.allowanceMap.foreach(item ⇒ {
          accountManager ! XAddressAllowanceUpdated(res.address, token = item._1, item._2)
        })
      })
      hex2BigInt(block.number) → block.hash
    }
  }

  def getAllTxReceipts(txHashs: Seq[String]): Future[Seq[XTransactionReceipt]] = {

    val taskPromise = Promise[Seq[XTransactionReceipt]]()

    val txReceipts = Map.empty[String, XTransactionReceipt]
    while (txReceipts.size < txHashs.size) {
      val txReceiptReqs = txHashs.filterNot(txReceipts.contains).map(hash ⇒ XGetTransactionReceiptReq(hash))
      val resp = (ethereumConnectionActor ? XBatchGetTransactionReceiptsReq(txReceiptReqs))
        .mapTo[XBatchGetTransactionReceiptsRes]
        .map(_.resps.map(_.result))
      val txReceiptOpts = Await.result(resp, 10 seconds)
      val receipts = txReceiptOpts.filter(_.nonEmpty)
      txReceipts.++(receipts.map(receipt ⇒ receipt.get.transactionHash → receipt.get))
    }
    taskPromise.future
  }

  implicit def hex2BigInt(hex: String): BigInt = {
    if (hex.startsWith("0x")) {
      BigInt(hex.substring(2), 16)
    } else {
      BigInt(hex, 16)
    }
  }

  implicit def bigInt2Hex(num: BigInt): String = {
    "0x" + num.toString(16)
  }

}
