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

package org.loopring.lightcone.persistence.dals

import com.google.inject.Inject
import com.google.inject.name.Named
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException
import com.typesafe.scalalogging.Logger
import org.loopring.lightcone.lib.{ErrorException, SystemTimeProvider}
import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.{GetResult, JdbcProfile}
import slick.basic._
import slick.lifted.Query
import scala.concurrent._
import scala.util.{Failure, Success}

trait SettlementTxDal extends BaseDalImpl[SettlementTxTable, SettlementTx] {
  def saveTx(tx: SettlementTx): Future[PersistSettlementTx.Res]
  // get all pending txs with given owner
  def getPendingTxs(request: GetPendingTxs.Req): Future[GetPendingTxs.Res]

  // update address's all txs status below or equals the given nonce to BLOCK
  def updateInBlock(request: UpdateTxInBlock.Req): Future[UpdateTxInBlock.Res]
}
