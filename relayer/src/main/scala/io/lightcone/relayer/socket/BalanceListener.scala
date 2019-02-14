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
import com.google.inject.Inject
import io.lightcone.core.Address
import io.lightcone.relayer.actors.MultiAccountManagerActor
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.core._

import scala.concurrent.{ExecutionContext, Future}

object BalanceListener {
  val eventName = "balance"
}

class BalanceListener @Inject()(
    implicit
    val system: ActorSystem,
    val ec: ExecutionContext,
    val timeout: Timeout,
    val actors: Lookup[ActorRef])
    extends WrappedDataListener[SubcriberBalanceAndAllowance.Req] {

  var clients = Seq.empty[WrappedSocketClient[SubcriberBalanceAndAllowance.Req]]
  def accountManager = actors.get(MultiAccountManagerActor.name)

  def queryData(
      req: SubcriberBalanceAndAllowance.Req
    ): Future[SubcriberBalanceAndAllowance.Res] = {
    val getBalanceAndAllowancesReq =
      GetBalanceAndAllowances.Req(address = req.owner, tokens = req.tokens)
    (accountManager ? getBalanceAndAllowancesReq)
      .mapAs[GetBalanceAndAllowances.Res]
      .map { res =>
        SubcriberBalanceAndAllowance.Res(
          owner = res.address,
          balanceAndAllowances = res.balanceAndAllowanceMap.map { data =>
            SubcriberBalanceAndAllowance.BalanceAndAllowance(
              address = data._1,
              balance = data._2.balance,
              allowance = data._2.allowance,
              avaliableBalance = data._2.availableBalance,
              avaliableAllowance = data._2.availableAllowance
            )
          }.toSeq
        )
      }
  }

  def onData(
      client: SocketIOClient,
      data: SubcriberBalanceAndAllowance.Req,
      ackSender: AckRequest
    ): Unit = {
    val wrappedSocketClient =
      new WrappedSocketClient(BalanceListener.eventName, client, data)
    val (_clients, outDatedClients) =
      clients.partition(!_.equals(wrappedSocketClient))
    outDatedClients.foreach(_.stop)
    wrappedSocketClient.start()(queryData)
    clients = _clients.+:(wrappedSocketClient)
  }

  def dataChanged(msg: Any): Unit = {
    clearDisConnectedClient
    msg match {
      case req: AddressBalanceOrAllowanceUpdated =>
        clients.foreach { client =>
          if (Address(req.owner)
                .equals(Address(client.req.owner)) && client.req.tokens
                .exists(token => Address(token).equals(Address(req.token)))) {
            client.restart()(queryData)
          }
        }
    }
  }

  def clearDisConnectedClient = {
    val (_clients, disConnectedClients) =
      clients.partition(_.client.isChannelOpen)
    clients = _clients
    disConnectedClients.foreach(_.stop)
  }
}
