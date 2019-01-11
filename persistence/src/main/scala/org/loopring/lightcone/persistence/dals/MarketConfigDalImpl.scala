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

package org.loopring.lightcone.persistence.dals

import com.google.inject.name.Named
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import scala.concurrent._
import com.google.inject.Inject
import org.loopring.lightcone.lib.TimeProvider

class MarketConfigDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-market-config") val dbConfig: DatabaseConfig[
      JdbcProfile
    ],
    timeProvider: TimeProvider)
    extends MarketConfigDal {
  val query = TableQuery[MarketConfigTable]

  def saveConfigs(configs: Seq[MarketConfig]): Future[Int] = insert(configs)

  def getAllConfigs(): Future[Seq[MarketConfig]] =
    db.run(query.filter(_.effective === 1).result)

  def getConfig(marketHash: String): Future[Option[MarketConfig]] =
    db.run(
      query
        .filter(_.marketHash === marketHash)
        .filter(_.effective === 1)
        .take(1)
        .result
        .headOption
    )

  def getConfig(
      primary: String,
      secondary: String
    ): Future[Option[MarketConfig]] =
    db.run(
      query
        .filter(_.primary === primary)
        .filter(_.secondary === secondary)
        .filter(_.effective === 1)
        .take(1)
        .result
        .headOption
    )

  def getConfigs(marketHashes: Seq[String]): Future[Seq[MarketConfig]] =
    db.run(query.filter(_.marketHash inSet marketHashes).result)

  def updateConfig(config: MarketConfig): Future[Int] = insertOrUpdate(config)
}
