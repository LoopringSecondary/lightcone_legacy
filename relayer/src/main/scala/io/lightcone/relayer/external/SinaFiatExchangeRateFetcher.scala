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
import scala.collection.JavaConverters._

class SinaFiatExchangeRateFetcher @Inject()(
    implicit
    val config: Config,
    val system: ActorSystem,
    val ec: ExecutionContext,
    val materializer: ActorMaterializer)
    extends FiatExchangeRateFetcher
    with Logging {

  private val currencyConfig = config.getConfig(ExternalCrawlerActor.name)
  private val baseCurrency = currencyConfig.getString("base_currency")
  private val currencies = currencyConfig
    .getStringList("currencies.fiat")
    .asScala
    .toSeq
  private val sinaParameterCurrencyMap = currencies.map { c =>
    s"fx_s${baseCurrency.toLowerCase}${c.toLowerCase}" -> c
  }.toMap
  private val uri = String.format(
    currencyConfig.getString("sina.uri"),
    sinaParameterCurrencyMap.keys.mkString(",")
  )

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
              val currencyArr = j
                .replaceAll("\"", "")
                .replaceAll("\r", "")
                .replaceAll("\n", "")
                .split(";")
              val currencyMap = currencyArr.map { c =>
                val currencyItem = c.split("=")
                assert(currencyItem.nonEmpty)
                val key = currencyItem(0).substring(11) // "var hq_str_fx_susdsgd" => "fx_susdsgd"
                val currencySymbol = sinaParameterCurrencyMap.getOrElse(
                  key,
                  throw ErrorException(
                    ErrorCode.ERR_INTERNAL_UNKNOWN,
                    s"not found request parameter:$key"
                  )
                )
                val pair = s"$baseCurrency-$currencySymbol"
                if (currencyItem.length == 1) {
                  log.error(
                    s"Sina not return currency correctly. request:${currencyItem(0)} response:$c"
                  )
                  pair -> 0.0
                } else {
                  val charArr = currencyItem(1).split(",")
                  // val formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                  // val time = formatter.parse(charArr(17) + " " + charArr(0)).getTime
                  // assert(time > 0)
                  val currency = charArr(1).toDouble
                  pair -> currency
                }
              }.toMap
              val result = currencyMap.filter(m => m._2 > 0)
              assert(result.nonEmpty)
              fillCurrencyTickersToPersistence(result)
            }

        case m =>
          log.error(s"get currency rate data from Sina failed:$m")
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            "Failed request Sina"
          )
      }
    } yield res

  private def fillCurrencyTickersToPersistence(
      exchangeRate: Map[String, Double]
    ) = {
    exchangeRate.map { k =>
      val price = scala.util
        .Try(
          (BigDecimal(1) / BigDecimal(k._2))
            .setScale(8, BigDecimal.RoundingMode.HALF_UP)
            .toDouble
        )
        .toOption
        .getOrElse(0.0)
      val currencies = k._1.split("-")
      new TokenTickerRecord(
        symbol = currencies(1),
        price = price,
        dataSource = "Sina"
      )
    }.toSeq
  }
}
