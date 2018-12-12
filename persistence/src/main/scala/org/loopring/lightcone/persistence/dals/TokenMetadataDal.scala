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

import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.core._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import scala.concurrent._
import org.slf4s.Logging

trait TokenMetadataDal
  extends BaseDalImpl[TokenMetadataTable, XTokenMetadata] {
  def getTokens(reloadFromDatabase: Boolean = false): Future[Seq[XTokenMetadata]]
}

class TokenMetadataDalImpl()(
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    val ec: ExecutionContext
) extends TokenMetadataDal with Logging {
  val query = TableQuery[TokenMetadataTable]

  private var tokens: Seq[XTokenMetadata] = Nil

  def getTokens(reloadFromDatabase: Boolean = false) = {
    if (reloadFromDatabase || tokens.isEmpty) {
      db.run(query.take(Int.MaxValue).result).map { tokens_ ⇒
        tokens = tokens_
        log.info(s"token metadata retrieved>> ${tokens.mkString("\n", "\n", "\n")}")
        tokens
      }
    } else {
      Future.successful(tokens)
    }
  }
}
