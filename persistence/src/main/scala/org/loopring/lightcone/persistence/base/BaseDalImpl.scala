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

trait BaseDalImpl[T <: BaseTable[A], A] extends BaseDal[T, A, String] {
  protected val query: TableQuery[T]
  protected val module: BaseDatabaseModule

  implicit lazy val db = module.db
  implicit lazy val profile = module.profile
  implicit lazy val ec = module.ec

  import profile.api._

  def insert(row: A): Future[String] = {
    insert(Seq(row)).map(_.head)
  }

  def insert(rows: Seq[A]): Future[Seq[String]] = {
    db.run(query returning query.map(_.id) ++= rows)
  }

  def findById(id: String): Future[Option[A]] = {
    db.run(query.filter(_.id === id).result.headOption)
  }

  def findByFilter[C: CanBeQueryCondition](f: (T) ⇒ C): Future[Seq[A]] = {
    db.run(query.withFilter(f).result)
  }

  def deleteById(id: String): Future[Int] = {
    deleteById(Seq(id))
  }

  def deleteById(ids: Seq[String]): Future[Int] = {
    db.run(query.filter(_.id.inSet(ids)).delete)
  }

  def deleteByFilter[C: CanBeQueryCondition](f: (T) ⇒ C): Future[Int] = {
    db.run(query.withFilter(f).delete)
  }

  def createTable(): Future[Unit] = {
    query.schema.create.statements.foreach(println)
    db.run(DBIO.seq(query.schema.create))
  }

  def displayTableSchema() = {
    query.schema.create.statements.foreach(println)
  }

}
