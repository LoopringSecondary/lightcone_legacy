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

import com.corundumstudio.socketio._
import io.lightcone.relayer.socketio._
import com.google.inject.Inject
import io.lightcone.lib._

class TransactionNotifier @Inject()
    extends SocketIONotifier[SocketIOSubscription.ParamsForTxRecord] {

  val eventName = "transactions"

  def wrapClient(
      client: SocketIOClient,
      req: SocketIOSubscription.ParamsForTxRecord
    ) =
    new SocketIOSubscriber(
      client,
      req.copy(addresses = req.addresses.map(Address.normalize))
    )

  def extractNotifyData(
      subscription: SocketIOSubscription.ParamsForTxRecord,
      event: AnyRef
    ): Option[AnyRef] =
    event match {
      case e: TxRecordUpdate =>
        if (subscription.addresses.contains(e.owner) &&
            (subscription.txTypes.isEmpty || subscription.txTypes.contains(
              e.txType
            ))) {
          Some(
            Transaction(
              gasUsed = e.gasUsed,
              hash = e.txHash,
              blockNum = e.blockNum,
              time = e.time,
              status = e.status
            )
          )
        } else None
      case _ => None
    }
}
