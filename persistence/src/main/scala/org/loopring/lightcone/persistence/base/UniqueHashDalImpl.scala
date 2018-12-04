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

trait UniqueHashDalImpl[T <: UniqueHashTable[A], A]
  extends UniqueHashDal[T, A] with BaseDalImpl[T, A] {
  import profile.api._

  def update(row: A): Future[Int] = {
    db.run(query.filter(_.hash === getRowHash(row)).update(row))
  }

  def update(rows: Seq[A]): Future[Unit] = {
    db.run(DBIO.seq(rows.map(r ⇒ query.filter(_.hash === getRowHash(r)).update(r)): _*))
  }

  def findByHash(hash: String): Future[Option[A]] = {
    db.run(query.filter(_.hash === hash).result.headOption)
  }
  def deleteByHash(hash: String): Future[Int] =
    deleteByHash(Seq(hash))

  def deleteByHash(hashes: Seq[String]): Future[Int] =
    db.run(query.filter(_.hash.inSet(hashes)).delete)
}

