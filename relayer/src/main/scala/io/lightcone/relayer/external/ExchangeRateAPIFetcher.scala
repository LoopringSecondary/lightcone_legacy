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

package io.lightcone.relayer.external

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.google.inject.Inject
import com.typesafe.config.Config
import io.lightcone.core.{ErrorCode, ErrorException}
import io.lightcone.persistence.TokenTickerRecord
import io.lightcone.relayer.actors.ExternalCrawlerActor
import org.slf4s.Logging
import scala.concurrent.ExecutionContext
import org.json4s._
import org.json4s.native.JsonMethods._

class ExchangeRateAPIFetcher @Inject()(
    implicit
    val config: Config,
    val system: ActorSystem,
    val ec: ExecutionContext,
    val materializer: ActorMaterializer)
    extends FiatExchangeRateFetcher
    with Logging {

  private val currencyConfig = config.getConfig(ExternalCrawlerActor.name)
  private val baseCurrency = currencyConfig.getString("base_currency")
  private val uri =
    s"${currencyConfig.getString("exchangerate.uri")}/${baseCurrency}"

  implicit val formats = DefaultFormats

  def fetchExchangeRates() =
    for {
      response <- Http().singleRequest(
        HttpRequest(
          method = HttpMethods.GET,
          uri = Uri(uri)
        )
      )
      res <- response match {
        case HttpResponse(StatusCodes.OK, _, entity, _) =>
          entity.dataBytes
            .map(_.utf8String)
            .runReduce(_ + _)
            .map { j =>
              /*{
                "base":"USD",
                "date":"2019-04-03",
                "time_last_updated":1554286322,
                "rates":{
                  "USD":1,
                  "AUD":1.41153166,
                  "CAD":1.33282692,
                  "CHF":0.99825594,
                  "CNY":6.7214362,
                  "EUR":0.89201931,
                  "GBP":0.76479757,
                  "HKD":7.84970753,
                  "ILS":3.61823014,
                  "INR":68.87786555,
                  "JPY":111.38021494,
                  "KRW":1137.40524964,
                  "MXN":19.13792238,
                  "MYR":4.0837263,
                  "NOK":8.59678897,
                  "NZD":1.477991,
                  "RUB":65.42496619,
                  "SEK":9.31267228,
                  "SGD":1.35588255,
                  "THB":31.76957704,
                  "TRY":5.60054616,
                  "ZAR":14.16934653
                }
              }
               */
              val json = parse(j)
              val rateMap =
                (json \ "rates").values.asInstanceOf[Map[String, Double]]
              rateMap.map { i =>
                val price = if (i._1.toUpperCase == baseCurrency.toUpperCase) {
                  1.0
                } else {
                  scala.util
                    .Try(
                      (BigDecimal(1) / BigDecimal(i._2))
                        .setScale(8, BigDecimal.RoundingMode.HALF_UP)
                        .toDouble
                    )
                    .toOption
                    .getOrElse(0.0)
                }
                new TokenTickerRecord(
                  symbol = i._1,
                  price = price,
                  dataSource = "ExchangeRate"
                )
              }.toSeq
            }

        case m =>
          log.error(s"get currency rate data from exchangerate-api failed:$m")
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            "Failed request exchangerate-api"
          )
      }
    } yield res
}
