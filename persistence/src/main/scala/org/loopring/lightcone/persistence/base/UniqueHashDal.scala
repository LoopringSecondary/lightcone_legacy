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
import scala.concurrent._

trait UniqueHashDal[T <: UniqueHashTable[A], A] extends BaseDal[T, A] {
  def getRowHash(row: A): String

  def update(row: A): Future[Int]
  def update(rows: Seq[A]): Future[Unit]

  def findByHash(hash: String): Future[Option[A]]
  def deleteByHash(hash: String): Future[Int]
  def deleteByHash(hashes: Seq[String]): Future[Int]
}
