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

package org.loopring.lightcone.biz.database.dals

import org.loopring.lightcone.biz.database.OrderDatabase
import org.loopring.lightcone.biz.database.base._
import org.loopring.lightcone.biz.database.entity.OrderEntity
import org.loopring.lightcone.biz.database.tables._
import org.loopring.lightcone.biz.enum.OrderStatus
import slick.dbio.Effect
import slick.jdbc.MySQLProfile.api._
import slick.sql.FixedSqlAction

import scala.concurrent.Future

case class QueryCondition(
    owner: Option[String] = None,
    market: Option[String] = None, status: Seq[String] = Seq(), orderHashes: Seq[String] = Seq(),
    orderType: Option[String] = None, side: Option[String] = None
)

trait OrdersDal extends BaseDalImpl[Orders, OrderEntity] {
  def getOrder(orderHash: String): Future[Option[OrderEntity]]
  def getOrders(condition: QueryCondition, skip: Int, take: Int): Future[Seq[OrderEntity]]
  def count(condition: QueryCondition): Future[Int]
  def getOrders(condition: QueryCondition): Future[Seq[OrderEntity]]
  def getOrdersWithCount(condition: QueryCondition, skip: Int, take: Int): (Future[Seq[OrderEntity]], Future[Int])
  def saveOrder(order: OrderEntity): Future[Int]
  def softCancelByMarket(owner: String, market: String): FixedSqlAction[Int, NoStream, Effect.Write]
  def softCancelByOwner(owner: String): FixedSqlAction[Int, NoStream, Effect.Write]
  def softCancelByTime(owner: String, cutoff: Long): FixedSqlAction[Int, NoStream, Effect.Write]
  def softCancelByHash(orderHash: String): FixedSqlAction[Int, NoStream, Effect.Write]
  def unwrapCondition(condition: QueryCondition): Query[Orders, OrderEntity, Seq]
}

class OrdersDalImpl(val module: OrderDatabase) extends OrdersDal {
  val query = ordersQ

  override def update(row: OrderEntity): Future[Int] = {
    db.run(query.filter(_.id === row.id).update(row))
  }

  override def update(rows: Seq[OrderEntity]): Future[Unit] = {
    db.run(DBIO.seq(rows.map(r ⇒ query.filter(_.id === r.id).update(r)): _*))
  }

  def saveOrder(order: OrderEntity): Future[Int] = module.db.run(query += order)

  def getOrder(orderHash: String): Future[Option[OrderEntity]] = {
    findByFilter(_.hash === orderHash).map(_.headOption)
  }

  def getOrders(condition: QueryCondition, skip: Int, take: Int): Future[Seq[OrderEntity]] = {

    db.run(unwrapCondition(condition)
      .drop(skip)
      .take(take)
      .result)
  }

  def count(condition: QueryCondition): Future[Int] = db.run(unwrapCondition(condition).length.result)

  // 用来查询所有相关订单， 没有分页参数，主要用来做软取消，慎用
  def getOrders(condition: QueryCondition): Future[Seq[OrderEntity]] = {
    db.run(unwrapCondition(condition)
      .result)
  }

  def getOrdersWithCount(condition: QueryCondition, skip: Int, take: Int): (Future[Seq[OrderEntity]], Future[Int]) = {

    val action = unwrapCondition(condition)
      .drop(skip)
      .take(take)

    (db.run(action.drop(skip).take(take).result), db.run(action.length.result))
  }

  def softCancelByMarket(owner: String, market: String): FixedSqlAction[Int, NoStream, Effect.Write] = {
    query
      .filter(_.owner === owner)
      .filter(_.market === market)
      .filter(_.status === OrderStatus.NEW.toString)
      .map(o ⇒ (o.status, o.updatedAt))
      .update(OrderStatus.CANCELLED_BY_USER.toString, System.currentTimeMillis / 1000)
  }

  def softCancelByOwner(owner: String): FixedSqlAction[Int, NoStream, Effect.Write] = {
    query
      .filter(_.owner === owner)
      .filter(_.status === OrderStatus.NEW.toString)
      .map(o ⇒ (o.status, o.updatedAt))
      .update(OrderStatus.CANCELLED_BY_USER.toString, System.currentTimeMillis / 1000)
  }

  def softCancelByTime(owner: String, cutoff: Long): FixedSqlAction[Int, NoStream, Effect.Write] = {
    query
      .filter(_.owner === owner)
      .filter(_.validUntil >= cutoff)
      .filter(_.status === OrderStatus.NEW.toString)
      .map(o ⇒ (o.status, o.updatedAt))
      .update(OrderStatus.CANCELLED_BY_USER.toString, System.currentTimeMillis / 1000)
  }

  def softCancelByHash(orderHash: String): FixedSqlAction[Int, NoStream, Effect.Write] = {
    query
      .filter(_.hash === orderHash)
      .filter(_.status === OrderStatus.NEW.toString)
      .map(o ⇒ (o.status, o.updatedAt))
      .update(OrderStatus.CANCELLED_BY_USER.toString, System.currentTimeMillis / 1000)
  }

  override def unwrapCondition(condition: QueryCondition): Query[Orders, OrderEntity, Seq] = {

    query
      .filter { o ⇒
        condition.owner.map(o.owner === _).getOrElse(true: Rep[Boolean])
      }
      .filter { o ⇒
        condition.orderType.map(o.orderType === _).getOrElse(true: Rep[Boolean])
      }
      .filter { o ⇒
        condition.side.map(o.side === _).getOrElse(true: Rep[Boolean])
      }
      .filter { o ⇒
        condition.market.map(o.market === _).getOrElse(true: Rep[Boolean])
      }
      .filter(_.status inSet condition.status)
      .filter(_.hash inSet condition.orderHashes)
      .sortBy(_.createdAt.desc)
  }
}
