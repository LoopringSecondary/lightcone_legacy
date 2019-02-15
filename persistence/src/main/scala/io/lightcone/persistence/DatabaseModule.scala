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

package io.lightcone.persistence

import com.google.inject.Inject
import com.google.inject.name.Named
import io.lightcone.persistence.dals._
import scala.concurrent._

class DatabaseModule @Inject()(
    val tokenMetadataDal: TokenMetadataDal,
    val orderDal: OrderDal,
    val tradeDal: TradeDal,
    val ringDal: RingDal,
    val blockDal: BlockDal,
    val settlementTxDal: SettlementTxDal,
    val marketMetadataDal: MarketMetadataDal,
    val missingBlocksRecordDal: MissingBlocksRecordDal,
    val ohlcDataDal: OHLCDataDal,
    val tokenTickerInfoDal: TokenTickerInfoDal,
    val orderService: OrderService,
    val tradeService: TradeService,
    val ringService: RingService,
    val blockService: BlockService,
    val settlementTxService: SettlementTxService,
    val ohlcDataService: OHLCDataService
  )(
    implicit
    @Named("db-execution-context") val ec: ExecutionContext)
    extends base.BaseDatabaseModule {

  val tables = Seq(
    tokenMetadataDal,
    orderDal,
    tradeDal,
    ringDal,
    blockDal,
    settlementTxDal,
    marketMetadataDal,
    missingBlocksRecordDal,
    ohlcDataDal,
    tokenTickerInfoDal
  )

  createTables()
}
