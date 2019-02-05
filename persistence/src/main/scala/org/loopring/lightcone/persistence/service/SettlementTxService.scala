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

package org.loopring.lightcone.persistence.service

import org.loopring.lightcone.persistence.dals.SettlementTxDal
import org.loopring.lightcone.proto._
import org.loopring.lightcone.core._
import scala.concurrent.Future

trait SettlementTxService {

  def saveTx(req: PersistSettlementTx.Req): Future[PersistSettlementTx.Res]
  // get all pending txs with given owner, from_nonce is a optional parameter(>=)
  def getPendingTxs(request: GetPendingTxs.Req): Future[GetPendingTxs.Res]

  // update address's all txs status below or equals the given nonce to BLOCK
  def updateInBlock(request: UpdateTxInBlock.Req): Future[UpdateTxInBlock.Res]
}
