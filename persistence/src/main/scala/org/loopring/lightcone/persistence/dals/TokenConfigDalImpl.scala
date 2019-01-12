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

class TokenConfigDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-token-config") val dbConfig: DatabaseConfig[
      JdbcProfile
    ],
    timeProvider: TimeProvider)
    extends TokenConfigDal {

  val query = TableQuery[TokenConfigTable]

  def saveConfigs(configs: Seq[TokenConfig]): Future[Int] = insert(configs)

  def getAllConfigs(): Future[Seq[TokenConfig]] =
    db.run(query.result)

  def updateConfig(config: TokenConfig): Future[Int] = insertOrUpdate(config)
}
