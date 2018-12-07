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
import akka.pattern.ask
import akka.util.Timeout
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.ethereum.abi.{ERC20ABI, WETHABI}
import org.loopring.lightcone.proto.actors._

import scala.util._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class EthereumDataIndexer(
    val actors: Lookup[ActorRef]
)(implicit
    ec: ExecutionContext,
    timeout: Timeout
)
  extends Actor with ActorLogging {

  val ethereumConnectionActor: ActorRef = actors.get("ethereum_connection")

  val erc20Abi = ERC20ABI()
  val wethAbi  = WETHABI()

  var currentBlockNumber: BigInt = BigInt(-1)
  var currentBlockHash: String = _

  override def preStart(): Unit = {
    //
    ethereumConnectionActor ? XEthBlockNumberReq() onComplete {
      case Success(XEthBlockNumberRes(_, _, result, None)) ⇒
        (ethereumConnectionActor ? XGetBlockWithTxHashByNumberReq(result)) onComplete {
          case Success(XGetBlockWithTxHashByNumberRes(_, _, block, None)) ⇒
            currentBlockNumber = result
            currentBlockHash = block.get.hash
        }
      case Success(XEthBlockNumberRes(_, _, _, error)) ⇒
        log.error(s"fail to get the current blockNumber:${error.get.error}")

      case Failure(e) ⇒
        log.error(s"fail to get the current blockNumber:${e.getMessage}")
        context.stop(self)
    }
  }

  override def receive: Receive = {
    case XStart ⇒ process()
    case XStop  ⇒ context.stop(self)
  }
  def process(): Unit = {
    for {
      taskNum ← (ethereumConnectionActor ? XEthBlockNumberReq())
        .mapTo[XEthBlockNumberRes]
        .map(_.result)

      block ← if (taskNum > currentBlockNumber)
        (ethereumConnectionActor ? XGetBlockWithTxObjectByNumberReq(currentBlockNumber + 1))
          .mapTo[XGetBlockWithTxObjectByNumberRes]
      //没有更高的块则等待
      else
        Future {
          Thread.sleep(15000)
        }
      nextTask ← block match {
        case XGetBlockWithTxObjectByNumberRes(_, _, Some(result), _) ⇒
          if (result.parentHash.equals(currentBlockHash))
            indexBlock(result)
          else
            handleFork(currentBlockNumber - 1)
        case _ ⇒
          Future.successful(currentBlockNumber, currentBlockHash)
      }
    } yield {
      currentBlockNumber = nextTask._1
      currentBlockHash = nextTask._2
      self ! XStart
    }
  }

  // find the fork height
  def handleFork(blockNum: BigInt): Future[(BigInt, String)] = {

    Future.successful((BigInt(0), ""))
  }

  // index block
  def indexBlock(block: XBlockWithTxObject): Future[(BigInt, String)] = {
    val txs = block.transactions
    val txReceiptReqs = txs.map(tx ⇒ XGetTransactionReceiptReq(tx.hash))
    for {
      txReceipts ← (ethereumConnectionActor ? XBatchGetTransactionReceiptsReq(txReceiptReqs))
        .mapTo[XBatchGetTransactionReceiptsRes]
        .map(_.resps)
      txWithReceipts = txs zip txReceipts.map(_.result.get)
    } yield {
      txWithReceipts.map(t ⇒ {
        //针对ETH的变化，暂时把token地址定位""
        val addresses = List(t._1.from → "")
        t._2.logs.foreach(log ⇒ {
          erc20Abi
        })
        addresses
      })
    }
    Future.successful((BigInt(0), ""))
  }

  implicit def hex2BigInt(hex: String): BigInt = {
    if (hex.startsWith("0x")) {
      BigInt(hex.substring(2), 16)
    } else {
      BigInt(hex, 16)
    }
  }

  implicit def BigInt2Hex(num: BigInt): String = {
    "0x" + num.toString(16)
  }

}
