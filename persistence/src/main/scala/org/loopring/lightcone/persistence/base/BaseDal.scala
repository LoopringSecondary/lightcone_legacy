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

import org.loopring.lightcone.persistence.PersistenceModule
import org.loopring.lightcone.persistence.table.BaseTable
import slick.jdbc.MySQLProfile.api._
import slick.lifted.CanBeQueryCondition

import scala.concurrent.Future

trait BaseDal[T, A] {
  //  def insert(row: A): Future[Long]
  //  def insert(rows: Seq[A]): Future[Seq[Long]]
  // def update(row: A): Future[Int]
  // def update(rows: Seq[A]): Future[Unit]
  //  def findById(id: Long): Future[Option[A]]
  def findByFilter[C: CanBeQueryCondition](f: (T) ⇒ C): Future[Seq[A]]
  //  def deleteById(id: Long): Future[Int]
  //  def deleteById(ids: Seq[Long]): Future[Int]
  def deleteByFilter[C: CanBeQueryCondition](f: (T) ⇒ C): Future[Int]
  def createTable(): Future[Unit]
  def displayTableSchema()
}

trait BaseDalImpl[T <: BaseTable[A], A] extends BaseDal[T, A] {
  val query: TableQuery[T]
  val module: PersistenceModule

  implicit val db = module.db
  implicit val profile = module.profile
  implicit val executor = module.dbec

  //  override def insert(row: A): Future[Long] = {
  //    insert(Seq(row)).map(_.head)
  //  }

  //  override def insert(rows: Seq[A]): Future[Seq[Long]] = {
  //    db.run(query returning query.map(_.id) ++= rows)
  //  }
  //
  //  override def findById(id: Long): Future[Option[A]] = {
  //    db.run(query.filter(_.id === id).result.headOption)
  //  }

  override def findByFilter[C: CanBeQueryCondition](f: (T) ⇒ C): Future[Seq[A]] = {
    db.run(query.withFilter(f).result)
  }

  //  override def deleteById(id: Long): Future[Int] = {
  //    deleteById(Seq(id))
  //  }

  //  override def deleteById(ids: Seq[Long]): Future[Int] = {
  //    db.run(query.filter(_.id.inSet(ids)).delete)
  //  }

  override def deleteByFilter[C: CanBeQueryCondition](f: (T) ⇒ C): Future[Int] = {
    db.run(query.withFilter(f).delete)
  }

  override def createTable(): Future[Unit] = {

    query.schema.create.statements.foreach(println)
    db.run(DBIO.seq(query.schema.create))
  }

  override def displayTableSchema() = {
    query.schema.create.statements.foreach(println)
  }

}
