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
import io.lightcone.core.ErrorCode
import io.lightcone.core.ErrorCode._
import io.lightcone.persistence._
import slick.basic._
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._
import scala.concurrent._
import scala.util.{Failure, Success}

class CMCTokenSlugDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-cmc-token-slug") val dbConfig: DatabaseConfig[
      JdbcProfile
    ])
    extends CMCTokenSlugDal {

  val query = TableQuery[CMCTokenSlugTable]

  def saveSlugs(slugs: Seq[CMCTokenSlug]): Future[ErrorCode] =
    db.run((query ++= slugs).asTry).map {
      case Failure(ex) => {
        logger.error(s"save tickers error : ${ex.getMessage}")
        ERR_PERSISTENCE_INTERNAL
      }
      case Success(x) =>
        ERR_NONE
    }

  def getAll(): Future[Seq[CMCTokenSlug]] =
    db.run(query.take(Integer.MAX_VALUE).result)

  def getBySlugs(slugs: Seq[String]): Future[Seq[CMCTokenSlug]] =
    db.run(query.filter(_.slug inSet slugs).result)

  def update(
      fromSlug: String,
      to: CMCTokenSlug
    ): Future[ErrorCode] =
    for {
      result <- db.run(
        query
          .filter(_.slug === fromSlug)
          .map(c => (c.slug, c.symbol))
          .update(to.slug, to.symbol)
      )
    } yield {
      if (result >= 1) ERR_NONE
      else ERR_PERSISTENCE_UPDATE_FAILED
    }
}
