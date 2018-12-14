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
import org.json4s.DefaultFormats
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.base.safefuture._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import scala.util._

private[ethereum] class EthereumClientMonitor(
    router: ActorRef,
    connectionPools: Seq[ActorRef],
    checkIntervalSeconds: Int
  )(
    implicit timeout: Timeout,
    ec: ExecutionContextExecutor)
    extends Actor
    with ActorLogging {

  implicit val formats = DefaultFormats

  context.system.scheduler.schedule(
    0.seconds,
    checkIntervalSeconds.seconds,
    self,
    XCheckBlockHeight()
  )

  def receive: Receive = {
    case _: XCheckBlockHeight =>
      log.debug("start scheduler check highest block...")
      val blockNumJsonRpcReq = JsonRpcReqWrapped(
        id = Random.nextInt(100),
        method = "eth_blockNumber",
        params = None
      )
      import JsonRpcResWrapped._
      connectionPools.map { g =>
        for {
          blockNumResp: Int <- (g ? blockNumJsonRpcReq.toProto)
            .mapAs[XJsonRpcRes]
            .map(toJsonRpcResWrapped)
            .map(_.result)
            .map(anyHexToInt)
            .recover {
              case e: Exception =>
                log.error(
                  s"exception on getting blockNumber: $g: ${e.getMessage}"
                )
                -1
            }
        } yield {
          router ! XNodeBlockHeight(
            path = g.path.toString,
            height = blockNumResp
          )
        }
      }
  }

  def anyHexToInt: PartialFunction[Any, Int] = {
    case s: String => BigInt(s.replace("0x", ""), 16).toInt
    case _         => -1
  }

}
