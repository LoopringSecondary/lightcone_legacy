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
import com.typesafe.scalalogging.Logger

trait BaseDatabaseModule {
  implicit val dbConfig: DatabaseConfig[JdbcProfile]
  implicit val config: Config
  implicit val ec: ExecutionContext
  private[this] val logger = Logger(this.getClass)

  val tables: Seq[BaseDal[_, _]]

  def createTables() = {
    try {
      Await.result(
        Future.sequence(tables.map(_.createTable)),
        10.second
      )
    } catch {
      case e: Exception if e.getMessage.contains("already exists") =>
        logger.info(e.getMessage)
      case e: Exception =>
        logger.error("Failed to create MySQL tables: " + e.getMessage)
        System.exit(0)
    }
  }

  def dropTables() = {
    try {
      Await.result(
        Future.sequence(tables.map(_.dropTable)),
        10.second
      )
    } catch {
      case e: Exception if e.getMessage.contains("Unknown table") =>
        logger.info(e.getMessage)
      case e: Exception =>
        logger.error("Failed to drop MySQL tables: " + e.getMessage)
        System.exit(0)
    }
  }

  def displayTableSchemas() = tables.map(_.displayTableSchema)
}
