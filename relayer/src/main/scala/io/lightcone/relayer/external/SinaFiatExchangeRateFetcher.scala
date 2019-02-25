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

import java.text.SimpleDateFormat
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import com.google.inject.Inject
import com.typesafe.config.Config
import io.lightcone.core.{ErrorCode, ErrorException}
import io.lightcone.relayer.actors.CMCCrawlerActor
import org.slf4s.Logging
import scala.concurrent.{ExecutionContext, Future}

class SinaFiatExchangeRateFetcher @Inject()(
    implicit
    val config: Config,
    val system: ActorSystem,
    val ec: ExecutionContext,
    val materializer: ActorMaterializer)
    extends FiatExchangeRateFetcher
    with Logging {

  val currencyConfig = config.getConfig(CMCCrawlerActor.name)

  val uri = currencyConfig.getString("sina.uri")

  // TODO(du):只定义接口支持多种法币，sina实现只支持USD-CNY,后续再找支持多种法币的接口实现
  def fetchExchangeRates(fiat: Seq[String]): Future[Map[String, Double]] =
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
              // var hq_str_fx_susdcny="12:50:00,6.7562,6.7559,6.7731,154,6.7642,6.7665,6.7511,6.7559,在岸人民币,-0.2,-0.0133,0.002277,Mecklai Financial Services. Mumbai,6.9762,6.2409,*******-,2019-02-18"
              val value = j.substring(j.lastIndexOf("=") + 1)
              val charArr = value
                .replaceAll("\"", "")
                .replaceAll(";", "")
                .replaceAll("\r", "")
                .replaceAll("\n", "")
                .split(",")
              val formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
              val time = formatter.parse(charArr(17) + " " + charArr(0)).getTime
              val currency = charArr(2).toDouble
              assert(currency > 0)
              assert(time > 0)
              Map(USD_RMB -> currency)
            }

        case m =>
          log.error(s"get currency rate data from Sina failed:$m")
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            "Failed request Sina"
          )
      }
    } yield res

}
