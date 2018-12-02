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

package org.loopring.lightcone.persistence.base

import slick.jdbc.MySQLProfile.api._
import slick.lifted.CanBeQueryCondition
import scala.concurrent.{ ExecutionContext, Future }

trait BaseDal[T <: BaseTable[A], A, I] {
  val query: TableQuery[T]

  def tableName = query.baseTableRow.tableName

  def insert(row: A): Future[I]
  def insert(rows: Seq[A]): Future[Seq[I]]
  def update(row: A): Future[Int]
  def update(rows: Seq[A]): Future[Unit]
  def findById(id: I): Future[Option[A]]
  def findByFilter[C: CanBeQueryCondition](f: (T) ⇒ C): Future[Seq[A]]
  def deleteById(id: I): Future[Int]
  def deleteById(ids: Seq[I]): Future[Int]
  def deleteByFilter[C: CanBeQueryCondition](f: (T) ⇒ C): Future[Int]
  def createTable(): Future[Any]
  def dropTable(): Future[Any]
  def displayTableSchema()
}
