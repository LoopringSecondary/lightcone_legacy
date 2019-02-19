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

package io.lightcone.relayer.actors

import akka.actor._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.cmc._
import io.lightcone.lib._
import io.lightcone.persistence.RequestJob.JobType
import io.lightcone.persistence._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.external.{CurrencyManager, SinaCurrencyManagerImpl}
import io.lightcone.relayer.jsonrpc._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

// Owner: Yongfeng
object CurrencyCrawlerActor extends DeployedAsSingleton {
  val name = "currency_crawler"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      dbModule: DatabaseModule,
      actors: Lookup[ActorRef],
      materializer: ActorMaterializer,
      currencyManager: CurrencyManager,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new CurrencyCrawlerActor()))
  }
}

class CurrencyCrawlerActor(
  )(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val materializer: ActorMaterializer,
    val dbModule: DatabaseModule,
    val currencyManager: CurrencyManager,
    val system: ActorSystem)
    extends InitializationRetryActor
    with JsonSupport
    with RepeatedJobActor
    with ActorLogging {
  val selfConfig = config.getConfig(CurrencyCrawlerActor.name)
  val refreshIntervalInSeconds = selfConfig.getInt("refresh-interval-seconds")
  val initialDelayInSeconds = selfConfig.getInt("initial-delay-in-seconds")

  private var currencyRate: Option[CurrencyRate] = None

  val repeatedJobs = Seq(
    Job(
      name = "sync_currency_datas",
      dalayInSeconds = refreshIntervalInSeconds,
      initialDalayInSeconds = initialDelayInSeconds,
      run = () => syncFromSina()
    )
  )

  override def initialize() = {
    val f = for {
      latestJob <- dbModule.requestJobDal.findLatest(
        JobType.CURRENCY_FROM_SINA
      )
      rate <- if (latestJob.nonEmpty) {
        dbModule.currencyRateDal.getCurrencyByJob(latestJob.get.batchId)
      } else {
        Future.successful(None)
      }
    } yield {
      if (rate nonEmpty) {
        currencyRate = rate
      }
    }
    f onComplete {
      case Success(_) =>
        becomeReady()
      case Failure(e) =>
        throw e
    }
    f
  }

  def ready: Receive = super.receiveRepeatdJobs orElse {
    case _: GetCurrencyRate.Req =>
      sender ! GetCurrencyRate.Res(currencyRate)
  }

  private def syncFromSina() = this.synchronized {
    log.info("CurrencyCrawlerActor run sync job")
    for {
      savedJob <- dbModule.requestJobDal.saveJob(
        RequestJob(
          jobType = JobType.CURRENCY_FROM_SINA,
          requestTime = timeProvider.getTimeSeconds()
        )
      )
      sinaResponse <- currencyManager.getUsdCnyCurrency()
      _ <- dbModule.requestJobDal.updateStatusCode(
        savedJob.batchId,
        200,
        timeProvider.getTimeSeconds()
      )
      _ <- persistCurrencyRate(savedJob.batchId, sinaResponse)
      _ <- dbModule.requestJobDal.updateSuccessfullyPersisted(
        savedJob.batchId
      )
    } yield {}
    Future.successful()
  }

  private def persistCurrencyRate(
      batchId: Int,
      currency: CurrencyData
    ) =
    for {
      _ <- Future.unit
      currencyRate_ = CurrencyRate(
        currency.currency,
        currency.timeInMillSecond,
        batchId
      )
      _ <- dbModule.currencyRateDal.save(currencyRate_)
    } yield {
      currencyRate = Some(currencyRate_)
    }
}
