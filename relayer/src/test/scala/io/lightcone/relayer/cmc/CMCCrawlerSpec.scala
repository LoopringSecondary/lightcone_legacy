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

package io.lightcone.relayer.cmc

import akka.pattern._
import io.lightcone.cmc.{CMCTickerData, TickerDataInfo, TickerStatus}
import io.lightcone.core._
import io.lightcone.persistence.RequestJob
import io.lightcone.persistence.RequestJob.JobType
import io.lightcone.relayer.actors._
import io.lightcone.relayer.data._
import io.lightcone.relayer.support._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scalapb.json4s.Parser

class CMCCrawlerSpec
    extends CommonSpec
    with HttpSupport
    with EthereumSupport
    with DatabaseModuleSupport
    with MetadataManagerSupport
    with CMCSupport {

  def crawlerActor = actors.get(CMCCrawlerActor.name)
  def refresherActor = actors.get(ExternalDataRefresher.name)

  val parser = new Parser(preservingProtoFieldNames = true) //protobuf 序列化为json不使用驼峰命名

  "cmc crawler" must {
    "get all tickers and persist" in {
      for {
        savedJob <- dbModule.requestJobDal.saveJob(
          RequestJob(
            jobType = JobType.TICKERS_FROM_CMC,
            requestTime = timeProvider.getTimeSeconds()
          )
        )
        cmcResponse <- getMockedCMCTickers()
        updated <- if (cmcResponse.data.nonEmpty) {
          val status = cmcResponse.status.getOrElse(TickerStatus())
          for {
            _ <- dbModule.requestJobDal.updateStatusCode(
              savedJob.batchId,
              status.errorCode,
              timeProvider.getTimeSeconds()
            )
            _ <- persistTickers(savedJob.batchId, cmcResponse.data)
            _ <- dbModule.requestJobDal.updateSuccessfullyPersisted(
              savedJob.batchId
            )
            tokens <- dbModule.tokenMetadataDal.getTokens()
            _ <- updateTokenPrice(cmcResponse.data, tokens)
          } yield true
        } else {
          Future.successful(false)
        }
      } yield {
        if (updated) {
          // TODO tokenMetadata reload

        }
      }
      val q1 = Await.result(
        getMockedCMCTickers()
          .mapTo[TickerDataInfo],
        30.second
      )
      q1
    }

  }

  private def getMockedCMCTickers() = {
    import scala.io.Source
    val fileContents = Source.fromResource("cmc.data").getLines.mkString

    val res = parser.fromJsonString[TickerDataInfo](fileContents)
    res.status match {
      case Some(r) if r.errorCode == 0 =>
        Future.successful(res.copy(data = res.data))
      case Some(r) if r.errorCode != 0 =>
        log.error(
          s"Failed request CMC, code:[${r.errorCode}] msg:[${r.errorMessage}]"
        )
        Future.successful(res)
      case m =>
        log.error(s"Failed request CMC, return:[$m]")
        Future.successful(TickerDataInfo(Some(TickerStatus(errorCode = 404))))
    }
  }

  private def updateTokenPrice(
      usdTickers: Seq[CMCTickerData],
      tokens: Seq[TokenMetadata]
    ) = {
    var changedTokens = Seq.empty[TokenMetadata]
    tokens.foreach { token =>
      val symbol = if (token.symbol == "WETH") "ETH" else token.symbol
      val priceQuote =
        usdTickers.find(_.symbol == symbol).flatMap(_.quote.get("USD"))
      val usdPriceQuote = priceQuote.getOrElse(
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"can not found ${symbol} price in USD"
        )
      )
      if (token.usdPrice != usdPriceQuote.price) {
        changedTokens = changedTokens :+ token.copy(
          usdPrice = usdPriceQuote.price
        )
      }
    }
    Future.sequence(changedTokens.map { token =>
      dbModule.tokenMetadataDal
        .updateTokenPrice(token.address, token.usdPrice)
        .map { r =>
          if (r != ErrorCode.ERR_NONE)
            log.error(s"failed to update token price:$token")
        }
    })
  }

  private def persistTickers(
      batchId: Int,
      tickers_ : Seq[CMCTickerData]
    ) =
    for {
      _ <- Future.unit
      tickersToPersist = tickerManager.convertCMCResponseToPersistence(
        batchId,
        tickers_
      )
      fixGroup = tickersToPersist.grouped(20).toList
      _ = fixGroup.map(dbModule.CMCTickersInUsdDal.saveTickers)
    } yield Unit
}
