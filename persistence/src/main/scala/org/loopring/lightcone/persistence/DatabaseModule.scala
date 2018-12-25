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
import org.loopring.lightcone.persistence.service._
import slick.basic._
import slick.jdbc.JdbcProfile
import scala.concurrent._

class DatabaseModule @Inject()(
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    @Named("db-execution-context") val ec: ExecutionContext)
    extends base.BaseDatabaseModule {

  val orderService: OrderService = new OrderServiceImpl()
  val tradeService: TradeService = new TradeServiceImpl()

  val orderCancelledEventService: OrdersCancelledEventService =
    new OrdersCancelledEventServiceImpl()
  val orderCutoffService: OrdersCutoffService = new OrdersCutoffServiceImpl()
  val tokenMetadataService = new TokenMetadataServiceImpl()

  val tables = Seq(
    new TokenMetadataDalImpl(),
    new OrderDalImpl(),
    new OrdersCancelledEventDalImpl(),
    new OrdersCutoffDalImpl(),
    new TradeDalImpl(),
    new AddressDalImpl(),
    new TokenBalanceDalImpl(),
    new BlockDalImpl(),
    new TransactionDalImpl(),
    new EventLogDalImpl(),
    new TokenTransferDalImpl(),
    new SettlementTxDalImpl()
  )
}
