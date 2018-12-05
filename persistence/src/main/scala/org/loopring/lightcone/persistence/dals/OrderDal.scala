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

import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.persistence._
import org.loopring.lightcone.proto.core._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import com.mysql.jdbc.exceptions.jdbc4._
import scala.concurrent._
import scala.util.{ Failure, Success }
import org.loopring.lightcone.persistence.utils._
import com.google.protobuf.ByteString

trait OrderDal
  extends BaseDalImpl[OrderTable, XRawOrder] {

  // Save a order to the database and returns the saved order and indicate
  // whether the order was perviously saved or not.
  // When a order is saved, make sure the following fields are NON-empty:
  // - string hash
  // - int32  version
  // - string owner
  // - string token_s
  // - string token_b
  // - bytes  amount_s
  // - bytes  amount_b
  // - int32  valid_since
  //
  // and the following fields are EMPTY:
  // - int64  id
  // - State state
  //
  // and the following files are kept as-is:
  // - Params params
  // - FeeParams fee_params
  // - ERC1400Params erc1400_params
  // also, if the order is NEW, the status field needs to save as NEW
  // and the created_at and updated_at fileds should both be the current timestamp;
  // if the order already exists, no field should be changed.
  def saveOrder(order: XRawOrder): Future[XSaveOrderResult]

  // Returns orders with given hashes
  def getOrdersByHash(hashes: Seq[String]): Future[Seq[XRawOrder]]
  def getOrder(hash: String): Future[Option[XRawOrder]]
}

class OrderDalImpl(val databaseModule: BaseDatabaseModule)(
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    val ec: ExecutionContext
) extends OrderDal {
  val query = TableQuery[OrderTable]
  def getRowHash(row: XRawOrder) = row.hash
  val timeProvider = new SystemTimeProvider()
  implicit val XOrderStatusCxolumnType = enumColumnType(XOrderStatus)
  implicit val XTokenStandardCxolumnType = enumColumnType(XTokenStandard)

  def saveOrder(order: XRawOrder): Future[XSaveOrderResult] = {
    val now = timeProvider.getTimeMillis
    val state = XOrderPersState.State(
      createdAt = now,
      updatedAt = now,
      status = XOrderStatus.STATUS_NEW
    )
    val feeParams = order.feeParams match {
      case Some(v) ⇒ (v.tokenFee, v.amountFee, v.walletSplitPercentage)
      case None    ⇒ ("", ByteString.EMPTY, 0)
    }
    val validUntil = order.params match {
      case Some(v) ⇒ v.validUntil
      case None    ⇒ 0
    }
    val persState = XOrderPersState(
      hash = order.hash,
      tokenS = order.tokenS,
      tokenB = order.tokenB,
      tokenFee = feeParams._1,
      amountS = order.amountS,
      amountB = order.amountB,
      amountFee = feeParams._2,
      validSince = order.validSince,
      validUntil = validUntil,
      walletSplitPercentage = feeParams._3,
      state = Some(state)
    )
    val a = (for {
      _ ← query ++= Seq(order)
      state ← databaseModule.orderStates.saveDBIO(persState)
    } yield state).transactionally
    db.run(a.asTry).map {
      case Failure(e: MySQLIntegrityConstraintViolationException) ⇒ {
        XSaveOrderResult(
          error = XPersistenceError.PERS_ERR_DUPLICATE_INSERT,
          order = None,
          alreadyExist = true
        )
      }
      case Failure(ex) ⇒ {
        // TODO du: print some log
        // log(s"error : ${ex.getMessage}")
        XSaveOrderResult(
          error = XPersistenceError.PERS_ERR_INTERNAL,
          order = None
        )
      }
      case Success(x) ⇒ XSaveOrderResult(
        error = XPersistenceError.PERS_ERR_NONE,
        order = Some(order)
      )
    }
  }

  def getOrdersByHash(hashes: Seq[String]): Future[Seq[XRawOrder]] = {
    if (hashes.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      db.run(query.filter(_.hash inSet hashes).result)
    }
  }

  def getOrder(hash: String): Future[Option[XRawOrder]] = getOrdersByHash(Seq(hash)).map(_.headOption)
}
