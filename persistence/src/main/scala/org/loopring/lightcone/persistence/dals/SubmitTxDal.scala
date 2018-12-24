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

import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException
import org.loopring.lightcone.lib.{MarketHashProvider, SystemTimeProvider}
import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import slick.lifted.Query
import scala.concurrent._
import scala.util.{Failure, Success}

trait SubmitTxDal extends BaseDalImpl[SubmitTxTable, XSubmitTx] {
  def saveTx(tx: XSubmitTx): Future[XErrorCode]
  def saveTxs(txs: Seq[XSubmitTx]): Future[XErrorCode]
  // get all pending txs with given owner, from_nonce is a optional parameter(>=)
  def getPendingTxs(request: XGetPendingTxsReq): Future[Seq[XSubmitTx]]
  // update address's all txs status below or equals the given nonce to BLOCK
  def updateInBlock(request: XUpdateTxInBlockReq): Future[XErrorCode]
}

class SubmitDalImpl(
  )(
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    val ec: ExecutionContext)
    extends SubmitTxDal {
  val query = TableQuery[SubmitTxTable]
  val timeProvider = new SystemTimeProvider()

  def saveTx(tx: XSubmitTx): Future[XErrorCode] = ???

  def saveTxs(txs: Seq[XSubmitTx]): Future[XErrorCode] = ???

  def getPendingTxs(request: XGetPendingTxsReq): Future[Seq[XSubmitTx]] = ???

  def updateInBlock(request: XUpdateTxInBlockReq): Future[XErrorCode] = ???
}
