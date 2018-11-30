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

package org.loopring.lightcone.auxiliary.database

import com.google.inject.Inject
import com.google.inject.name.Named
import org.loopring.lightcone.auxiliary.database.dals._
import org.loopring.lightcone.ethereum.time.TimeProvider
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext

class MySQLOrderDatabase @Inject() (
    val dbConfig: DatabaseConfig[JdbcProfile],
    val timeProvider: TimeProvider,
    @Named("db-execution-context") val dbec: ExecutionContext
) extends OrderDatabase {

  val orders = new OrdersDalImpl(this)
  val orderChangeLogs = new OrderChangeLogsDalImpl(this)
  val blocks = new BlocksDalImpl(this)

  def generateDDL(): Unit = Seq(
    orders.createTable(),
    orderChangeLogs.createTable(),
    blocks.createTable()
  )

  def displayDDL(): Unit = {
    orders.displayTableSchema()
    orderChangeLogs.displayTableSchema()
    blocks.displayTableSchema()
  }
}
