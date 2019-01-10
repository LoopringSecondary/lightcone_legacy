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
import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import scala.concurrent._

trait TokenBalanceDal extends BaseDalImpl[TokenBalanceTable, TokenBalance] {
  def getBalances(address: String): Future[Seq[TokenBalance]]

  def getBalance(
      address: String,
      token: String
    ): Future[Option[TokenBalance]]
}

class TokenBalanceDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-token-balance") val dbConfig: DatabaseConfig[
      JdbcProfile
    ])
    extends TokenBalanceDal {
  val query = TableQuery[TokenBalanceTable]

  def getBalances(address: String) =
    findByFilter(_.address === address)

  def getBalance(
      address: String,
      token: String
    ) =
    findByFilter(r => r.address === address && r.token === token)
      .map(_.headOption)
}
