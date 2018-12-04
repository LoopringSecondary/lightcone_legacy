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
import org.loopring.lightcone.actors.ethereum.JsonRpcResWrapped.toJsonRpcResWrapped
import org.loopring.lightcone.proto.actors.{ XJsonRpcErr, XJsonRpcReq, XJsonRpcRes }

import scala.util._

class EthereumServiceRouter(
    connectionActors: Seq[ActorRef]
)(
    implicit
    timeout: Timeout
) extends Actor with ActorLogging {

  var httpConnectionPools: Seq[(ActorRef, Int)] = connectionActors.map(_ -> 0)
  implicit val ec = context.system.dispatcher

  //TODO（yadong） 如果EthereumClientMonitor在初始化的时候立刻做一次CheckBlockHeight，这里是不是可以考虑去掉，保持同样的代码只有一处
  //或者在EthereumServiceRouter 中定时做检查，直接去掉EthereumClientMonitor
  override def preStart(): Unit = {
    val blockNumJsonRpcReq = JsonRpcReqWrapped(
      id = Random.nextInt(100),
      method = "eth_blockNumber",
      params = None
    )
    httpConnectionPools.foreach(g ⇒ {
      for {
        blockNumResp: Int ← (g._1 ? blockNumJsonRpcReq.toProto)
          .mapTo[XJsonRpcRes]
          .map(toJsonRpcResWrapped)
          .map {
            case JsonRpcResWrapped(_, _, result: String, _) ⇒
              BigInt(result.replace("0x", ""), 16).toInt
            case _ ⇒ -1
          }
          .recover {
            case e: Throwable ⇒
              log.error(s"exception on getting blockNumber: ${g._1}: ${e.getMessage}")
              -1
          }
      } yield {
        httpConnectionPools =
          (httpConnectionPools.toMap + (g._1 → blockNumResp)).toSeq.sortWith(_._2 > _._2)
      }
    })
  }

  override def receive: Receive = {
    case pool: (ActorRef, Int) ⇒
      httpConnectionPools =
        (httpConnectionPools.toMap + pool).toSeq.sortWith(_._2 > _._2)

    case (msg, height: Int) if msg.isInstanceOf[XJsonRpcReq] || msg.isInstanceOf[ProtoBuf[_]] ⇒
      val validPools = httpConnectionPools.filter(_._2 >= height)
      if (validPools.nonEmpty) {
        validPools(Random.nextInt(validPools.size))._1.forward(msg)
      } else {
        sender() ! XJsonRpcErr(message = "No accessible Ethereum node service")
      }

    case msg if msg.isInstanceOf[XJsonRpcReq] || msg.isInstanceOf[ProtoBuf[_]] ⇒ {
      if (httpConnectionPools.nonEmpty) {
        httpConnectionPools.head._1.forward(msg)
      } else {
        sender() ! XJsonRpcErr(message = "No accessible Ethereum node service")
      }
    }
    case msg ⇒
      log.error(s"unsupported request to EthereumServiceRouter: $msg")
  }
}
