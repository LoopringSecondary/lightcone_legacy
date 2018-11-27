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

package org.loopring.lightcone.actors.auxiliary.marketcap

import akka.actor.{ Actor, ActorRef, Timers }
import akka.pattern.ask
import akka.util.Timeout
import org.loopring.lightcone.auxiliary.marketcap.crawler.TokenIcoCrawler
import org.loopring.lightcone.proto.auxiliary._

import scala.concurrent.duration._

class TokenIcoCrawlerActor(
    tokenIcoServiceActor: ActorRef, // TokenIcoServiceActor
    tokenInfoServiceActor: ActorRef, // TokenInfoServiceActor
    crawler: TokenIcoCrawler
) extends Actor with Timers {

  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.system.dispatcher

  type JDoc = org.jsoup.nodes.Document

  override def preStart(): Unit = {
    //daily schedule token's ico info
    timers.startPeriodicTimer("cronSyncTokenIcoInfo", "syncTokenIcoInfo", 24 hours)
  }

  override def receive: Receive = {
    case _: String ⇒
      val f = (tokenInfoServiceActor ? XGetTokenListReq()).mapTo[XGetTokenListRes]
      f.foreach {
        _.list.foreach { tokenInfo ⇒
          crawler.crawlTokenIcoInfo(tokenInfo.protocol) match {
            case Some(v) ⇒ tokenIcoServiceActor ! v
            case None    ⇒ //do nothing
          }
        }
      }
  }
}
