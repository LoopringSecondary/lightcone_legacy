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
import akka.routing._
import akka.util.Timeout
import org.json4s.DefaultFormats
import org.loopring.lightcone.proto.actors._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Random

private[ethereum] class EthereumClientMonitor(
    requestRouterActor: ActorRef,
    connectorGroups: Seq[ActorRef],
    checkIntervalSeconds: Int,
    healthyThreshold: Float
)(
    implicit
    timeout: Timeout
)
  extends Actor
  with ActorLogging {

  implicit val ec = context.system.dispatcher
  private val size = connectorGroups.size
  implicit val formats = DefaultFormats

  private val errCheckBlockHeightResp =
    XCheckBlockHeightResp(currentBlock = 1, heightBlock = 0)

  context.system.scheduler.schedule(
    checkIntervalSeconds.seconds,
    checkIntervalSeconds.seconds,
    self,
    XCheckBlockHeight()
  )

  def receive: Receive = {

    case _: XCheckBlockHeight ⇒
      log.info("start scheduler check highest block...")
      val syncingJsonRpcReq = JsonRpcReqWrapped(
        id = Random.nextInt(100),
        jsonrpc = "2.0",
        method = "eth_syncing",
        params = None
      )
      val blockNumJsonRpcReq = JsonRpcReqWrapped(
        id = Random.nextInt(100),
        jsonrpc = "2.0",
        method = "eth_blockNumber",
        params = None
      )
      import JsonRpcResWrapped._
      for {
        resps: Seq[(ActorRef, XCheckBlockHeightResp)] ← Future.sequence(
          connectorGroups.map { g ⇒
            for {
              syncingResp ← (g ? syncingJsonRpcReq.toProto)
                .mapTo[XJsonRpcRes]
                .map(toJsonRpcResWrapped)
                .map(_.result)
                .map(toCheckBlockHeightResp)
                .recover {
                  case e: TimeoutException ⇒
                    log.error(
                      s"timeout on getting blockheight: $g: ${e.getMessage}"
                    )
                    errCheckBlockHeightResp
                  case e: Throwable ⇒
                    log.error(
                      s"exception on getting blockheight: $g: ${e.getMessage}"
                    )
                    errCheckBlockHeightResp
                }
              // get each node block number
              blockNumResp ← (g ? blockNumJsonRpcReq.toProto)
                .mapTo[XJsonRpcRes]
                .map(toJsonRpcResWrapped)
                .map(_.result)
                .map(anyHexToInt)
              // heightBlcok = if(!syncing) currentBlock else syncing['height_block']
            } yield {
              val heightBlock = Seq(syncingResp.heightBlock, blockNumResp).max
              log.info(
                s"{ currentBlock: ${blockNumResp}, highestBlock: ${heightBlock} } @ ${g.path}"
              )
              (
                g,
                syncingResp.copy(
                  currentBlock = blockNumResp,
                  heightBlock = heightBlock
                )
              )
            }
          }
        )
        heightBNInGroup = resps.map(_._2.heightBlock).max
        goodGroupsOption = Seq(10, 20, 30)
          .map { i ⇒
            // 计算最高块和当前块的差距
            resps.filter(x ⇒ heightBNInGroup - x._2.currentBlock <= i).map(_._1)
          }
          .find(_.size >= size * healthyThreshold)
      } yield {
        // remove all routees
        connectorGroups.foreach { g ⇒
          requestRouterActor ! RemoveRoutee(ActorRefRoutee(g))
        }
        // only add Some(routee)
        goodGroupsOption.foreach {
          _.foreach { g ⇒
            log.info(s"added to connectorGroup: ${g.path}")
            val r = ActorSelectionRoutee(context.actorSelection(g.path))
            requestRouterActor ! AddRoutee(r)
          }
        }

        log.info(
          s"GoodGroups: ${goodGroupsOption.map(_.size).getOrElse(0)} connectorGroup are still in good shape, " +
            s"in connectorGroup height block number: ${heightBNInGroup}, end scheduler"
        )
      }
  }

  def toCheckBlockHeightResp: PartialFunction[Any, XCheckBlockHeightResp] = {
    case m: Map[_, _] ⇒
      val currentBlock =
        m.find(_._1 == "currentBlock").map(_._2).map(anyHexToInt).getOrElse(0)
      val heightBlock =
        m.find(_._1 == "highestBlock").map(_._2).map(anyHexToInt).getOrElse(10)
      XCheckBlockHeightResp(currentBlock, heightBlock)
    case b: Boolean ⇒
      if (b) errCheckBlockHeightResp else XCheckBlockHeightResp(1, 10)
    case _ ⇒ errCheckBlockHeightResp
  }

  def anyHexToInt: PartialFunction[Any, Int] = {
    case s: String ⇒ BigInt(s.replace("0x", ""), 16).toInt
    case _         ⇒ 0
  }

}

