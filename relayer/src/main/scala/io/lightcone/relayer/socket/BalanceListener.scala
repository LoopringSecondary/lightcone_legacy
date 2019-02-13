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

package io.lightcone.relayer.socket

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern._
import akka.util.Timeout
import com.corundumstudio.socketio.{AckRequest, SocketIOClient}
import com.corundumstudio.socketio.listener.DataListener
import io.lightcone.relayer.actors.MultiAccountManagerActor
import io.lightcone.relayer.base._
import io.lightcone.relayer.data.GetBalanceAndAllowances

import scala.concurrent.{ExecutionContext, Future}

object BalanceListener {
  val name = ""
}

class BalanceListener(
  )(
    implicit
    system: ActorSystem,
    ec: ExecutionContext,
    timeout: Timeout,
    actors: Lookup[ActorRef])
    extends DataListener[GetBalanceAndAllowances.Req] {

  def accountManager = actors.get(MultiAccountManagerActor.name)

  def queryData(
      req: GetBalanceAndAllowances.Req
    ): Future[GetBalanceAndAllowances.Res] = {
    (accountManager ? req).mapAs[GetBalanceAndAllowances.Res]
  }

  def onData(
      client: SocketIOClient,
      data: GetBalanceAndAllowances.Req,
      ackSender: AckRequest
    ): Unit = {}
}
