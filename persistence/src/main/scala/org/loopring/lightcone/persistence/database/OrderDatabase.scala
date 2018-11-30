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

package org.loopring.lightcone.persistence.database

import org.loopring.lightcone.persistence.database.dals._
import org.loopring.lightcone.ethereum.time.TimeProvider
import slick.basic._
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

trait OrderDatabase {
  val dbConfig: DatabaseConfig[JdbcProfile]
  val timeProvider: TimeProvider

  def profile: JdbcProfile = dbConfig.profile
  def db: BasicProfile#Backend#Database = dbConfig.db
  def dbec: ExecutionContext
  def displayDDL(): Unit
  def generateDDL(): Unit

  // table dal
  val orders: OrdersDal
  val orderChangeLogs: OrderChangeLogsDal
  val blocks: BlocksDal
}
