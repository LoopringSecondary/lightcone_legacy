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
import io.lightcone.persistence._
import slick.basic._
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._
import scala.concurrent._

class CMCTickersInUsdDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-cmc-tickers-in-usd") val dbConfig: DatabaseConfig[
      JdbcProfile
    ])
    extends CMCTickersInUsdDal {

  val query = TableQuery[CMCTickersInUsdTable]

  def saveTickers(tickers: Seq[CMCTickersInUsd]): Future[Unit] =
    for {
      _ <- db.run(query ++= tickers)
    } yield Unit

  def getTickersByJob(jobId: Int): Future[Seq[CMCTickersInUsd]] =
    db.run(query.filter(_.batchId === jobId).result)
}
