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
import org.loopring.lightcone.proto.actors._

import scala.util._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class EthereumDataIndexer(
    val actors: Lookup[ActorRef]
)(implicit
    ec: ExecutionContext,
    timeout: Timeout
)
  extends Actor with ActorLogging {

  val ethereumConnectionActor: ActorRef = actors.get("ethereum_connection")
  var currentBlockNumber: BigInt = BigInt(-1)
  var currentBlockHash: String = _

  override def preStart(): Unit = {
    ethereumConnectionActor ? XEthBlockNumberReq() onComplete {
      case Success(XEthBlockNumberRes(_, _, result, None)) ⇒
        currentBlockNumber = result

      case Success(XEthBlockNumberRes(_, _, _, error)) ⇒
        log.error(s"fail to get the current blockNumber:${error.get.error}")

      case Failure(e) ⇒
        log.error(s"fail to get the current blockNumber:${e.getMessage}")
    }

  }

  override def receive: Receive = {
    case "start" ⇒ process()
  }

  def process(): Unit = {
    (ethereumConnectionActor ? XEthBlockNumberReq())
      .map {
        case XEthBlockNumberRes(_, _, result, None) ⇒
          if (result > currentBlockNumber) {
            (ethereumConnectionActor ? XGetBlockWithTxObjectByNumberReq(currentBlockNumber + 1)).map {
              case XGetBlockWithTxObjectByNumberRes(_, _, res, None) if res.nonEmpty ⇒
                if (res.get.parentHash.equals(currentBlockHash)) {
                  indexBlock(res.get)
                } else {
                  handleFork(currentBlockNumber - 1)
                }
            }
          } else if (currentBlockNumber.equals(hex2BigInt(result))) {
            (ethereumConnectionActor ? XGetBlockWithTxHashByNumberReq(currentBlockNumber))
              .map {
                case XGetBlockWithTxHashByNumberRes(_, _, res, None) if res.nonEmpty ⇒
                  if (res.get.hash.equals(currentBlockHash)) {
                    //当前高度已经是最高高度，15秒以后执行下一次请求
                    context.system.scheduler.scheduleOnce(15 seconds)(process())
                  } else {
                    handleFork(currentBlockNumber - 1)
                  }
              }
          } else {
            // 如果最新高度低于已处理的高度说明分叉了
            handleFork(result - 1)
          }
      }

  }

  // find the fork height
  def handleFork(blockNum: BigInt): Unit = {

  }

  def indexBlock(block: XBlockWithTxObject): Unit = {

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
