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

package org.loopring.lightcone.persistence.marketcap.crawler

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Timers }
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, HttpResponse, StatusCodes }
import akka.util.Timeout
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import org.loopring.lightcone.persistence.marketcap.util.{ HttpConnector, SignatureUtil }
import org.loopring.lightcone.proto.persistence._
import scalapb.json4s.Parser

import scala.concurrent.duration._

class MarketTickerCrawlerImpl(
    implicit
    val system: ActorSystem,
    val mat: ActorMaterializer
) extends HttpConnector with MarketTickerCrawler with SignatureUtil {

  implicit val timeout = Timeout(5 seconds)

  val appId = system.settings.config.getString("my_token.app_id")
  val connection = http(system.settings.config.getString("my_token.host_url"))
  val appSecret = system.settings.config.getString("my_token.app_secret")

  val parser = new Parser(preservingProtoFieldNames = true) //protobuf 序列化为json不使用驼峰命名

  override def crawlMarketPairTicker(tokenInfo: XTokenInfo): Unit = {

    val (name_id, symbol, anchor) = if (tokenInfo.symbol == "ETH" || tokenInfo.symbol == "WETH") {
      ("ethereum", "eth", "usd")
    } else (tokenInfo.source, tokenInfo.symbol, "eth")

    val timestamp = System.currentTimeMillis() / 1000
    val sighTemp = s"anchor=$anchor&app_id=$appId&name_id=$name_id&symbol=${symbol.toLowerCase()}&timestamp=${timestamp}"
    val signValue = bytesToHex(getHmacSHA256(appSecret, s"$sighTemp&app_secret=$appSecret")).toUpperCase()
    val uri = s"/ticker/paironmarket?$sighTemp&sign=$signValue"

    get(HttpRequest(uri = uri, method = HttpMethods.GET)) {
      case HttpResponse(StatusCodes.OK, _, entity, _) ⇒

        entity.dataBytes.map(_.utf8String).runReduce(_ + _).map { dataInfoStr ⇒

          val marketTickData = parser.fromJsonString[XMarketTickData](dataInfoStr)

          val lastUpdated = marketTickData.timestamp

          marketTickData.data.foreach {
            _.marketList.foreach {
              case XMarketPair(exchange, symbol, market,
                price, priceCny, priceUsd,
                volume24hUsd, volume24h, volume24hFrom,
                percentChangeUtc0, alias) ⇒
                XExchangeTickerInfo(symbol, market, exchange,
                  price.toDouble, priceUsd.toDouble, priceCny.toDouble,
                  volume24hUsd.toDouble, volume24hFrom.toDouble, volume24h.toDouble,
                  percentChangeUtc0.toDouble, alias, lastUpdated)
            }
          }
        }

      case _ ⇒
        Future.successful(Unit)
    }

  }

  //todo 后续看是否需要特殊处理double类型的字段
  def toDouble: PartialFunction[String, Double] = {
    case s: String ⇒ scala.util.Try(s.toDouble).toOption.getOrElse(0)
  }

}

