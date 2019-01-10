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

import com.typesafe.config.Config
import slick.basic._
import slick.jdbc.JdbcProfile
import scala.concurrent.duration._
import scala.concurrent._

trait BaseDatabaseModule {
  implicit val dbConfig: DatabaseConfig[JdbcProfile]
  implicit val config: Config
  implicit val ec: ExecutionContext

  val tables: Seq[BaseDal[_, _]]

  def createTables() = tables.map(_.createTable)
  def dropTables() = tables.map(_.dropTable)
  def displayTableSchemas() = tables.map(_.displayTableSchema)
}
