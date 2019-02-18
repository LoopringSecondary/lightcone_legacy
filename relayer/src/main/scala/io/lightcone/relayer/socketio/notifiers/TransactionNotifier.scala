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
    extends SocketIONotifier[SubcribeTransaction] {

  val eventName = "transactions"

  def wrapClient(
      client: SocketIOClient,
      req: SubcribeTransaction
    ) =
    new SocketIOSubscriber(
      client,
      req.copy(addresses = req.addresses.map(Address.normalize))
    )

  // TODO(yadong):implement this
  def shouldNotifyClient(
      request: SubcribeTransaction,
      event: AnyRef
    ): Boolean = ???

  // def onEvent(msg: TransactionRecord): Unit = {
  //   msg match {
  //     case record: TransactionRecord =>
  //       clients.foreach { client =>
  //         if (client.req.address.equals(record.owner) &&
  //             (client.req.`type`.isEmpty || record.recordType.value == Numeric
  //               .toBigInt(client.req.`type`)
  //               .intValue()) &&
  //             (client.req.status.isEmpty || record.getHeader.txStatus.name.toLowerCase
  //               .contains(client.req.status.toLowerCase()))) {
  //           val header = record.getHeader
  //           val _data = TransactionResponse(
  //             owner = record.owner,
  //             //TODO(yadong) Transaction Record 中data 和 nonce 未保留
  //             transactions = Seq(
  //               Transaction(
  //                 from = header.txFrom,
  //                 to = header.txTo,
  //                 value = header.txValue,
  //                 gasPrice = Numeric
  //                   .toHexStringWithPrefix(BigInteger.valueOf(header.gasPrice)),
  //                 gasLimit = Numeric
  //                   .toHexStringWithPrefix(BigInteger.valueOf(header.gasLimit)),
  //                 gasUsed = Numeric
  //                   .toHexStringWithPrefix(BigInteger.valueOf(header.gasUsed)),
  //                 data = "0x0",
  //                 nonce = "0x0",
  //                 hash = header.txHash,
  //                 blockNum = Numeric.toHexStringWithPrefix(
  //                   BigInteger.valueOf(header.blockNumber)
  //                 ),
  //                 time = Numeric.toHexStringWithPrefix(
  //                   BigInteger.valueOf(header.blockTimestamp)
  //                 ),
  //                 status = header.txStatus.name.substring(10)
  //               )
  //             )
  //           )
  //           client.sendEvent(_data)
  //         }
  //       }
  //     case _ =>
  //   }

}
