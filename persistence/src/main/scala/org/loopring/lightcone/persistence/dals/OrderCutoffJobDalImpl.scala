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

import com.google.inject.Inject
import com.google.inject.name.Named
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto.{OrderCutoffJob, OrderStatusMonitor}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._
import scala.concurrent.{ExecutionContext, Future}

class OrderCutoffJobDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-order-cutoff-job") val dbConfig: DatabaseConfig[
      JdbcProfile
    ])
    extends OrderCutoffJobDal {
  val query = TableQuery[OrderCutoffJobTable]

  def saveJob(job: OrderCutoffJob): Future[Boolean] =
    insertOrUpdate(job).map(_ > 0)

  def getJobs(): Future[Seq[OrderCutoffJob]] =
    db.run(query.result)

  def deleteJob(job: OrderCutoffJob): Future[Boolean] =
    db.run(
        query
          .filter(_.broker === job.broker)
          .filter(_.owner === job.owner)
          .filter(_.tradingPair === job.tradingPair)
          .filter(_.cutoff === job.cutoff)
          .delete
      )
      .map(_ > 0)
}
