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

package io.lightcone.relayer.integration.starter

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import com.google.inject.{Guice, Injector}
import com.typesafe.config.ConfigFactory
import io.lightcone.core.MarketMetadata.Status._
import io.lightcone.core.MetadataManager
import io.lightcone.lib.TimeProvider
import io.lightcone.persistence._
import io.lightcone.relayer.CoreModule
import io.lightcone.relayer.actors._
import io.lightcone.relayer.base.Lookup
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.{CoreDeployerForTest, Metadatas}
import io.lightcone.relayer.integration.Metadatas._
import io.lightcone.relayer.integration.helper._
import net.codingwell.scalaguice.InjectorExtensions._
import org.rnorth.ducttape.TimeoutException
import org.rnorth.ducttape.unreliables.Unreliables
import org.testcontainers.containers.ContainerLaunchException

import scala.concurrent._

class IntegrationStarter extends MockHelper with DbHelper with MetadataHelper {

  var injector: Injector = _

  def starting(
    )(
      implicit
      timeout: Timeout,
      timeProvider: TimeProvider
    ) = {
    setDefaultEthExpects()
    val config = ConfigFactory.load()
    injector = Guice.createInjector(new CoreModule(config, true))
    implicit val dbModule = injector.instance[DatabaseModule]
    implicit val metadataManager = injector.instance[MetadataManager]
    implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

    prepareDbModule(dbModule)
    prepareMetadata(TOKENS, MARKETS, externalTickers)

    injector
      .instance[CoreDeployerForTest]
      .deploy()

    val actors = injector.instance[Lookup[ActorRef]]

    waiting(actors, metadataManager)
  }

  private def waiting(
      actors: Lookup[ActorRef],
      metadataManager: MetadataManager
    )(
      implicit
      timeout: Timeout,
      ec: ExecutionContext
    ) = {
    Thread.sleep(5000) //waiting for system
    //waiting for market

    try Unreliables.retryUntilTrue(
      10,
      TimeUnit.SECONDS,
      () => {
        val f =
          Future.sequence(metadataManager.getMarkets(ACTIVE, READONLY).map {
            meta =>
              val marketPair = meta.getMetadata.marketPair.get
              actors.get(MarketManagerActor.name) ? GetOrderbookSlots
                .Req(Some(marketPair))
          })
        val res =
          Await.result(f.mapTo[Seq[GetOrderbookSlots.Res]], timeout.duration)
        res.nonEmpty
      }
    )
    catch {
      case e: TimeoutException =>
        throw new ContainerLaunchException(
          "Timed out waiting for connectionPools init.)"
        )
    }

    //waiting for orderbookmanager
    try Unreliables.retryUntilTrue(
      10,
      TimeUnit.SECONDS,
      () => {
        val f =
          Future.sequence(metadataManager.getMarkets(ACTIVE, READONLY).map {
            meta =>
              val marketPair = meta.getMetadata.marketPair.get
              val orderBookInit = GetOrderbook.Req(0, 100, Some(marketPair))
              actors.get(OrderbookManagerActor.name) ? orderBookInit
          })
        val res =
          Await.result(f.mapTo[Seq[GetOrderbook.Res]], timeout.duration)
        res.nonEmpty
      }
    )
    catch {
      case e: TimeoutException =>
        throw new ContainerLaunchException(
          "Timed out waiting for connectionPools init.)"
        )
    }

    //waiting activity
    try Unreliables.retryUntilTrue(
      10,
      TimeUnit.SECONDS,
      () => {
        val f =
          (actors.get(ActivityActor.name) ? GetActivities.Req(
            paging = Some(CursorPaging(size = 10))
          )).mapTo[GetActivities.Res]
        val res = Await.result(f, timeout.duration)
        res.activities.isEmpty || res.activities.nonEmpty
      }
    )
    catch {
      case e: TimeoutException =>
        throw new ContainerLaunchException(
          "Timed out waiting for MetadataManagerActor init.)"
        )
    }

    //waiting for metadata
    try Unreliables.retryUntilTrue(
      10,
      TimeUnit.SECONDS,
      () => {
        val f = (actors.get(MetadataRefresher.name) ? GetTokens.Req())
          .mapTo[GetTokens.Res]
        val res = Await.result(f, timeout.duration)
        res.tokens.nonEmpty
        true
      }
    )
    catch {
      case e: TimeoutException =>
        throw new ContainerLaunchException(
          "Timed out waiting for MetadataRefresher init.)"
        )
    }
    try Unreliables.retryUntilTrue(
      10,
      TimeUnit.SECONDS,
      () => {
        val f = (actors.get(MetadataRefresher.name) ? GetTokens.Req())
          .mapTo[GetTokens.Res]
        val res = Await.result(f, timeout.duration)
        res.tokens.nonEmpty
        true
      }
    )
    catch {
      case e: TimeoutException =>
        throw new ContainerLaunchException(
          "Timed out waiting for MetadataRefresher init.)"
        )
    }
    //waiting for accountmanager

    //waiting for ethereum

  }

}
