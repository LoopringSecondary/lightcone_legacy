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

package org.loopring.lightcone.actors.persistence.marketcap

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Timers }
import akka.stream.ActorMaterializer
import org.loopring.lightcone.persistence.marketcap.crawler.TokenTickerCrawler

import scala.concurrent.duration._

class TokenTickerCrawlerActor(tokenTickerServiceActor: ActorRef, crawler: TokenTickerCrawler)(
    implicit
    val system: ActorSystem,
    val mat: ActorMaterializer
) extends Actor with Timers with ActorLogging {

  implicit val ec = system.dispatcher

  override def preStart(): Unit = {
    //daily schedule token's ticker info
    timers.startPeriodicTimer("cronSyncTokenTicker", "syncTokenTicker", 600 seconds)
  }

  override def receive: Receive = {
    case _: String ⇒
      val result = crawler.crawlTokenTickers()
      result.grouped(20).foreach {
        group ⇒
          tokenTickerServiceActor ! crawler.convertTO(group)
      }

      //todo 待cmc会员充值开通后，单独获取cny的ticker可以去掉
      Thread.sleep(50)
      crawler.getTokenTickers("CNY").foreach {
        _.grouped(100).foreach {
          batch ⇒ tokenTickerServiceActor ! crawler.convertTO(batch)
        }
      }

  }

}
