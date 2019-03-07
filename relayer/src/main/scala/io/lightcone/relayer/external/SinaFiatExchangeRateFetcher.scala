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
import io.lightcone.core.{Currency, ErrorCode, ErrorException}
import io.lightcone.persistence.TokenTickerRecord
import io.lightcone.relayer.actors.ExternalCrawlerActor
import org.slf4s.Logging
import io.lightcone.relayer.implicits._
import scala.concurrent.ExecutionContext

class SinaFiatExchangeRateFetcher @Inject()(
    implicit
    val config: Config,
    val system: ActorSystem,
    val ec: ExecutionContext,
    val materializer: ActorMaterializer)
    extends FiatExchangeRateFetcher
    with Logging {

  val currencyConfig = config.getConfig(ExternalCrawlerActor.name)

  val uri = currencyConfig.getString("sina.uri")

  def fetchExchangeRates(fiat: Seq[String]) =
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
                assert(currencyItem.length == 2)
                val key = currencyItem(0)
                val charArr = currencyItem(1).split(",")
//              val formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
//              val time = formatter.parse(charArr(17) + " " + charArr(0)).getTime
//                assert(time > 0)
                val currency = charArr(2).toDouble
                assert(currency > 0)
                if (key.indexOf("hq_str_fx_susdcny") > -1) {
                  USD_RMB -> currency
                } else if (key.indexOf("hq_str_fx_susdjpy") > -1) {
                  USD_JPY -> currency
                } else if (key.indexOf("hq_str_fx_seurusd") > -1) {
                  USD_EUR -> toDouble(
                    BigDecimal(1) / BigDecimal(currency)
                  )
                } else if (key.indexOf("hq_str_fx_sgbpusd") > -1) {
                  USD_GBP -> toDouble(
                    BigDecimal(1) / BigDecimal(currency)
                  )
                } else {
                  throw ErrorException(
                    ErrorCode.ERR_INTERNAL_UNKNOWN,
                    s"unsupport value: $c"
                  )
                }
              }.toMap

              fillCurrencyTickersToPersistence(fiat.map { f =>
                f -> currencyMap.getOrElse(f, 0.0)
              }.toMap)
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
      val currency = Currency
        .fromName(currencies(1))
        .getOrElse(
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            s"not found Currency of name:${currencies(1)}"
          )
        )
      new TokenTickerRecord(
        symbol = currencies(1),
        slug = currency.getSlug(),
        tokenAddress = currency.getAddress(),
        price = price,
        dataSource = "Sina"
      )
    }.toSeq
  }

  private def toDouble(bigDecimal: BigDecimal): Double =
    scala.util
      .Try(bigDecimal.setScale(4, BigDecimal.RoundingMode.HALF_UP).toDouble)
      .toOption
      .getOrElse(0)

}
