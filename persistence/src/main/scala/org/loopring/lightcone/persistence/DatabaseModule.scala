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
import com.typesafe.config.Config
import org.loopring.lightcone.persistence.dals._
import org.loopring.lightcone.persistence.service._
import slick.basic._
import scala.concurrent._

class DatabaseModule @Inject()(
    val tokenMetadataDal: TokenMetadataDal,
    val orderDal: OrderDal,
    val tradeDal: TradeDal,
    val tokenBalanceDal: TokenBalanceDal,
    val blockDal: BlockDal,
    val settlementTxDal: SettlementTxDal,
    val orderStatusMonitorDal: OrderStatusMonitorDal,
    val orderService: OrderService,
    val orderStatusMonitorService: OrderStatusMonitorService,
    val tokenMetadataService: TokenMetadataService,
    val tradeService: TradeService,
    val settlementTxService: SettlementTxService
  )(
    implicit
    @Named("db-execution-context") val ec: ExecutionContext)
    extends base.BaseDatabaseModule {

  val tables = Seq(
    tokenMetadataDal,
    orderDal,
    tradeDal,
    tokenBalanceDal,
    blockDal,
    settlementTxDal,
    orderStatusMonitorDal
  )

  createTables()
}
