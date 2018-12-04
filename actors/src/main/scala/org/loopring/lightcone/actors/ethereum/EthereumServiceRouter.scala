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
import akka.util.Timeout
import org.loopring.lightcone.proto.actors.{ XJsonRpcErr, XJsonRpcReq, XNodeBlockHeight, XRpcReqWithHeight }

import scala.util._

class EthereumServiceRouter(
    connectionActors: Seq[String]
)(
    implicit
    timeout: Timeout
) extends Actor with ActorLogging {

  var httpConnectionPools: Seq[(String, Int)] = connectionActors.map(_ → 0)

  override def receive: Receive = {
    case node: XNodeBlockHeight ⇒
      httpConnectionPools =
        (httpConnectionPools.toMap + (node.path → node.height)).toSeq.sortWith(_._2 > _._2)

    case req: XRpcReqWithHeight ⇒
      val validPools = httpConnectionPools.filter(_._2 >= req.height)
      if (validPools.nonEmpty) {
        context.actorSelection(validPools(Random.nextInt(validPools.size))._1).forward(req.req)
      } else {
        sender() ! XJsonRpcErr(message = "No accessible Ethereum node service")
      }

    case msg: XJsonRpcReq ⇒ {
      if (httpConnectionPools.nonEmpty) {
        context.actorSelection(httpConnectionPools.head._1).forward(msg)
      } else {
        sender() ! XJsonRpcErr(message = "No accessible Ethereum node service")
      }
    }

    case msg: ProtoBuf[_] ⇒ {
      if (httpConnectionPools.nonEmpty) {
        context.actorSelection(httpConnectionPools.head._1).forward(msg)
      } else {
        sender() ! XJsonRpcErr(message = "No accessible Ethereum node service")
      }
    }
    case msg ⇒
      log.error(s"unsupported request to EthereumServiceRouter: $msg")
  }
}
