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

import slick.lifted.CanBeQueryCondition
import slick.basic._
import slick.jdbc.JdbcProfile
import scala.concurrent._

trait BaseDalImpl[T <: BaseTable[A], A] extends BaseDal[T, A] {
  implicit val ec: ExecutionContext
  val dbConfig: DatabaseConfig[JdbcProfile]

  val profile = dbConfig.profile
  val db: JdbcProfile#Backend#Database = dbConfig.db

  import profile.api._

  def insert(row: A): Future[Int] = insert(Seq(row))

  def insert(rows: Seq[A]): Future[Int] = {
    db.run(query ++= rows).map(_.getOrElse(0))
  }

  def insertOrUpdate(row: A): Future[Int] = {
    db.run(query.insertOrUpdate(row))
  }

  def findByFilter[C: CanBeQueryCondition](f: (T) => C): Future[Seq[A]] = {
    db.run(query.withFilter(f).result)
  }

  def deleteByFilter[C: CanBeQueryCondition](f: (T) => C): Future[Int] = {
    db.run(query.withFilter(f).delete)
  }

  def findById(id: String): Future[Option[A]] = {
    db.run(query.filter(_.id === id).result.headOption)
  }

  def deleteById(id: String): Future[Int] = deleteById(Seq(id))

  def deleteById(ids: Seq[String]): Future[Int] =
    db.run(query.filter(_.id.inSet(ids)).delete)

  def createTable(): Future[Any] = {
    // query.schma.create.statements.foreach(println)
    db.run(DBIO.seq(query.schema.create))
  }

  def dropTable(): Future[Any] = {
    db.run(DBIO.seq(query.schema.drop))
  }

  def displayTableSchema() = {
    query.schema.create.statements.foreach(println)
  }

  def take(
      size: Int,
      skip: Int = 0
    ): Future[Seq[A]] =
    db.run(query.drop(skip).take(size).result)

}
