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

package io.lightcone.persistence.dals

import com.google.inject.Inject
import com.google.inject.name.Named
import io.lightcone.core._
import io.lightcone.lib.TimeProvider
import io.lightcone.persistence.TokenTickerInfo
import org.slf4s.Logging
import slick.basic._
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._
import scala.concurrent._

class TokenTickerInfoDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-token-ticker-info") val dbConfig: DatabaseConfig[
      JdbcProfile
    ],
    timeProvider: TimeProvider)
    extends TokenTickerInfoDal
    with Logging {
  val query = TableQuery[TokenTickerInfoTable]

  def saveOrUpdate(tickers: Seq[TokenTickerInfo]): Future[Seq[Int]] =
    Future.sequence(tickers.map(insertOrUpdate))

  def getAll(): Future[Seq[TokenTickerInfo]] =
    db.run(query.take(Int.MaxValue).result)
}
