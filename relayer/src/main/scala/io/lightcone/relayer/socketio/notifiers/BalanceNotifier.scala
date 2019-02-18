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

package io.lightcone.relayer.socketio.notifiers

import akka.actor.ActorSystem
import com.corundumstudio.socketio._
import io.lightcone.relayer.socketio._
import com.google.inject.Inject
import com.typesafe.config.Config
import io.lightcone.lib.Address

import scala.concurrent.ExecutionContext

class BalanceNotifier @Inject()(
    implicit
    val system: ActorSystem,
    val ec: ExecutionContext,
    val config: Config)
    extends SocketIONotifier[SubcribeBalanceAndAllowance] {

  val eventName = "balances"

  def wrapClient(
      client: SocketIOClient,
      req: SubcribeBalanceAndAllowance
    ) =
    new SocketIOSubscriber(
      client,
      req.copy(
        addresses = req.addresses.map(Address.normalize),
        tokens = req.tokens.map(Address.normalize)
      )
    )

  // TODO(yadong):implement this
  def shouldNotifyClient(
      request: SubcribeBalanceAndAllowance,
      event: AnyRef
    ): Boolean = ???

  // override def onEvent(msg: GetBalanceAndAllowances.Res): Unit = {
  //   msg match {
  //     // TODO(yadong): we need to use event struct, not RPC resp

  //     case res: GetBalanceAndAllowances.Res =>
  //       clients.foreach { client =>
  //         if (client.req.addresses.exists(res.address.equals)
  //             && (client.req.tokens
  //               .intersect(res.balanceAndAllowanceMap.keys.toSeq)
  //               .nonEmpty || client.req.tokens.isEmpty)) {
  //           val data = BalanceAndAllowanceResponse(
  //             owner = res.address,
  //             balanceAndAllowances = res.balanceAndAllowanceMap.filter { ba =>
  //               client.req.tokens.isEmpty || client.req.tokens.exists(
  //                 ba._1.equals
  //               )
  //             }.map { ba =>
  //               TokenBalanceAndAllowance(
  //                 address = ba._1,
  //                 balance = ba._2.balance,
  //                 allowance = ba._2.allowance,
  //                 availableBalance = ba._2.balance,
  //                 availableAllowance = ba._2.allowance
  //               )
  //             }.toSeq
  //           )
  //           client.sendEvent(data)
  //         }
  //       }
  //     case _ =>
  //   }
  // }
}
