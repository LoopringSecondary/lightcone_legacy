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

package org.loopring.lightcone.persistence

import com.google.inject.Inject
import com.google.inject.name.Named
import org.loopring.lightcone.persistence.dals._
import slick.basic._
import slick.jdbc.JdbcProfile
import scala.concurrent._

class DatabaseModule @Inject() (
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    @Named("db-execution-context") val ec: ExecutionContext
) extends base.BaseDatabaseModule {

  val orders: OrderDal = new OrderDalImpl()
  val trades: TradeDal = new TradeDalImpl()
  val addresses: AddressDal = new AddressDalImpl()
  val tokenBalances: TokenBalanceDal = new TokenBalanceDalImpl()
  val blocks: BlockDal = new BlockDalImpl()
  val transactions: TransactionDal = new TransactionDalImpl()
  val evengLogs: EventLogDal = new EventLogDalImpl()
  val tokenTransfers: TokenTransferDal = new TokenTransferDalImpl()

  val tables = Seq(
    orders,
    trades,
    addresses,
    tokenBalances,
    blocks,
    transactions,
    evengLogs,
    tokenTransfers
  )
}
