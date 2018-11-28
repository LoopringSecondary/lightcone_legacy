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

package org.loopring.lightcone.auxiliary.marketcap.crawler

import org.jsoup.Jsoup
import org.loopring.lightcone.proto.auxiliary._

class TokenIcoCrawlerImpl extends TokenIcoCrawler {

  type JDoc = org.jsoup.nodes.Document

  override def crawlTokenIcoInfo(tokenAddress: String): Option[XTokenIcoInfo] = {
    import collection.JavaConverters._

    val doc = get(s"https://etherscan.io/token/$tokenAddress#tokenInfo")
    val trs = doc.getElementsByTag("tr").asScala

    val tdsMap = trs.filter(_.childNodeSize() == 7).map { tr ⇒
      val childs = tr.children()
      childs.first().text().trim → childs.last().text().trim
    } toMap

    if (tdsMap.nonEmpty) {
      val icoStartDate = tdsMap.getOrElse("ICO Start Date", "")
      val icoEndDate = tdsMap.getOrElse("ICO End Date", "")
      val hardCap = tdsMap.getOrElse("Hard Cap", "")
      val softCap = tdsMap.getOrElse("Soft Cap", "")
      val raised = tdsMap.getOrElse("Raised", "")
      val icoPrice = tdsMap.getOrElse("ICO Price", "")
      val country = tdsMap.getOrElse("Country", "")

      Some(XTokenIcoInfo(
        tokenAddress = tokenAddress,
        icoStartDate = icoStartDate,
        icoEndDate = icoEndDate,
        hardCap = hardCap,
        softCap = softCap,
        tokenRaised = raised,
        icoPrice = icoPrice,
        country = country
      ))
    } else
      None
  }

  private def toTrimEth: PartialFunction[String, String] = {
    case str: String ⇒ str.replaceAll("ETH", "").trim
  }

  private def get(url: String): JDoc = Jsoup.connect(url).get()
}
