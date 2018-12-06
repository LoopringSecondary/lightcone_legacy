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
import org.loopring.lightcone.proto.actors._

import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.concurrent.duration._
import scala.util._

private[ethereum] class EthereumClientMonitor(
    router: ActorRef,
    connectionPools: Seq[ActorRef],
    checkIntervalSeconds: Int
)(implicit
    timeout: Timeout,
    ec: ExecutionContextExecutor
)
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
    case _: XCheckBlockHeight ⇒
      log.info("start scheduler check highest block...")
      val blockNumJsonRpcReq = JsonRpcReqWrapped(
        id = Random.nextInt(100),
        method = "eth_blockNumber",
        params = None
      )
      import JsonRpcResWrapped._
      Future.sequence(connectionPools.map { g ⇒
        for {
          blockNumResp: Int ← (g ? blockNumJsonRpcReq.toProto)
            .mapTo[XJsonRpcRes]
            .map(toJsonRpcResWrapped)
            .map(_.result)
            .map(anyHexToInt)
            .recover {
              case e: Exception ⇒
                log.error(s"exception on getting blockNumber: $g: ${e.getMessage}")
                -1
            }
        } yield {
          XNodeBlockHeight(path = g.path.toString, height = blockNumResp)
        }
      }).onComplete {
        case Success(nodes) ⇒
          val accessibleNodes = nodes.filter(_.height < 0).sortWith(_.height > _.height)
          router ! accessibleNodes.head

        case Failure(e) ⇒
          log.error(s"check Ethereum node height failed: ${e.getMessage}")
          router ! XNodeBlockHeight()
      }
  }
  def anyHexToInt: PartialFunction[Any, Int] = {
    case s: String ⇒ BigInt(s.replace("0x", ""), 16).toInt
    case _         ⇒ -1
  }

}

