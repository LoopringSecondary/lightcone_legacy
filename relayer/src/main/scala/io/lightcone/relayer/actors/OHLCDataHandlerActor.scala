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
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.relayer.base._
import io.lightcone.lib._
import io.lightcone.persistence.DatabaseModule
import io.lightcone.core.ErrorCode._
import io.lightcone.relayer.data._
import io.lightcone.core._

import scala.concurrent.ExecutionContext

object OHLCDataHandlerActor extends DeployedAsSingleton {
  val name = "ohlc_data_handler"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new OHLCDataHandlerActor()))
  }
}

class OHLCDataHandlerActor(
  )(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule)
    extends InitializationRetryActor {
  val selfConfig = config.getConfig(OHLCDataHandlerActor.name)

  def ready: Receive = {
    case data: OHLCRawData =>
      (for {
        saveRes <- dbModule.ohlcDataDal.saveData(data)
      } yield {
        saveRes.error match {
          case ERR_NONE =>
            saveRes.record
          case _ =>
            throw ErrorException(
              saveRes.error,
              s"failed to save ohlcRawData: $data"
            )
        }
      }) sendTo sender

    case req: GetOHLCData.Req =>
      dbModule.ohlcDataService.getOHLCData(req).sendTo(sender)
  }
}
