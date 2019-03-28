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

package io.lightcone.persistence.dals

import io.lightcone.core.ErrorCode
import io.lightcone.ethereum.event.BlockEvent
import io.lightcone.persistence.SettlementTx
import io.lightcone.persistence.base._
import scala.concurrent._

trait SettlementTxDal extends BaseDalImpl[SettlementTxTable, SettlementTx] {
  def saveTx(tx: SettlementTx): Future[ErrorCode]

  def getPendingTxs(
      owner: String,
      timeBefore: Long
    ): Future[Seq[SettlementTx]]

  def updateInBlock(
      txHash: String,
      from: String,
      nonce: Long
    ): Future[ErrorCode]

  def cleanTxsForReorg(event: BlockEvent): Future[Int]
}
