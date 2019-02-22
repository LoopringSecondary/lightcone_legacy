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

import io.lightcone.persistence.base._
import io.lightcone.relayer.data._
import scala.concurrent._

// TODO(yongfeng): delete this?
trait SettlementTxDal extends BaseDalImpl[SettlementTxTable, SettlementTx] {
  def saveTx(tx: SettlementTx): Future[PersistSettlementTx.Res]
  // get all pending txs with given owner
  def getPendingTxs(request: GetPendingTxs.Req): Future[GetPendingTxs.Res]

  // update address's all txs status below or equals the given nonce to BLOCK
  def updateInBlock(request: UpdateTxInBlock.Req): Future[UpdateTxInBlock.Res]
}
