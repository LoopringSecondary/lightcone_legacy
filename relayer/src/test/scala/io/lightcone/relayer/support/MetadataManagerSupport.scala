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

package io.lightcone.relayer.support

import java.util.concurrent.TimeUnit

import io.lightcone.relayer.actors._
import io.lightcone.relayer.validator._
import io.lightcone.relayer.data._
import io.lightcone.core._
import org.rnorth.ducttape.TimeoutException
import org.rnorth.ducttape.unreliables.Unreliables
import org.testcontainers.containers.ContainerLaunchException
import akka.pattern._
import io.lightcone.core.MetadataManager

import scala.concurrent.Await

trait MetadataManagerSupport extends DatabaseModuleSupport {
  me: CommonSpec =>

  actors.add(MetadataManagerActor.name, MetadataManagerActor.start)
  actors.add(
    MetadataManagerValidator.name,
    MessageValidationActor(
      new MetadataManagerValidator(),
      MetadataManagerActor.name,
      MetadataManagerValidator.name
    )
  )
  try Unreliables.retryUntilTrue(
    10,
    TimeUnit.SECONDS,
    () => {
      val f =
        (actors.get(MetadataManagerActor.name) ? LoadTokenMetadata.Req())
          .mapTo[LoadTokenMetadata.Res]
      val res = Await.result(f, timeout.duration)
      res.tokens.nonEmpty
    }
  )
  catch {
    case e: TimeoutException =>
      throw new ContainerLaunchException(
        "Timed out waiting for MetadataManagerActor init.)"
      )
  }
  metadataManager.reset(
    TOKENS.map(MetadataManager.normalize),
    MARKETS.map(MetadataManager.normalize)
  )

  actors.add(MetadataRefresher.name, MetadataRefresher.start)

  try Unreliables.retryUntilTrue(
    10,
    TimeUnit.SECONDS,
    () => {
      val f = (actors.get(MetadataRefresher.name) ? GetMetadatas.Req())
        .mapTo[GetMetadatas.Res]
      val res = Await.result(f, timeout.duration)
      res.markets.nonEmpty && res.tokens.nonEmpty
    }
  )
  catch {
    case e: TimeoutException =>
      throw new ContainerLaunchException(
        "Timed out waiting for MetadataRefresher init.)"
      )
  }
}
