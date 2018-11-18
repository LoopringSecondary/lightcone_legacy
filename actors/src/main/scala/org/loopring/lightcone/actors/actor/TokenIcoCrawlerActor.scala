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

package org.loopring.lightcone.actors.actor

import akka.actor.{ Actor, ActorRef, Timers }
import akka.pattern.{ ask }
import akka.util.Timeout
import org.jsoup.Jsoup
import org.loopring.lightcone.proto.auxiliary._

import scala.concurrent.duration._

class TokenIcoCrawlerActor(
    tokenIcoServiceActor: ActorRef, // TokenIcoServiceActor
    tokenInfoServiceActor: ActorRef // TokenInfoServiceActor
) extends Actor with Timers {

  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.system.dispatcher

  type JDoc = org.jsoup.nodes.Document

  override def preStart(): Unit = {
    //daliy schedule token's ico info
    timers.startPeriodicTimer("cronSyncTokenIcoInfo", "syncTokenIcoInfo", 24 hours)
  }

  override def receive: Receive = {
    case _: String ⇒
      val f = (tokenInfoServiceActor ? GetTokenListReq()).mapTo[GetTokenListRes]
      f.foreach {
        _.list.foreach { tokenInfo ⇒
          crawlTokenIcoInfo(tokenInfo.protocol)
        }
      }
  }

  private def crawlTokenIcoInfo(tokenAddress: String): Unit = {
    import collection.JavaConverters._

    val doc = get(s"https://etherscan.io/token/$tokenAddress#tokenInfo")
    val trs = doc.getElementsByTag("tr").asScala

    val tdsMap = trs.filter(_.childNodeSize() == 7).map { tr ⇒
      val childs = tr.children()
      (childs.first().text().trim → childs.last().text().trim)
    } toMap

    if (!tdsMap.isEmpty) {
      val icoStartDate = tdsMap.get("ICO Start Date").getOrElse("")
      val icoEndDate = tdsMap.get("ICO End Date").getOrElse("")
      val hardCap = tdsMap.get("Hard Cap").getOrElse("")
      val softCap = tdsMap.get("Soft Cap").getOrElse("")
      val raised = tdsMap.get("Raised").getOrElse("")
      val icoPrice = tdsMap.get("ICO Price").getOrElse("")
      val country = tdsMap.get("Country").getOrElse("")

      val tokenIcoInfo = TokenIcoInfo(
        tokenAddress = tokenAddress,
        icoStartDate = icoStartDate,
        icoEndDate = icoEndDate,
        hardCap = hardCap,
        softCap = softCap,
        tokenRaised = raised,
        icoPrice = icoPrice,
        country = country
      )

      tokenIcoServiceActor ! tokenIcoInfo
    }
  }

  private def toTrimEth: PartialFunction[String, String] = {
    case str: String ⇒ str.replaceAll("ETH", "").trim
  }

  private def get(url: String): JDoc = Jsoup.connect(url).get()
}
