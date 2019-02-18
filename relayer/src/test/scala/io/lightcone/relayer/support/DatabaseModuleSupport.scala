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

package io.lightcone.relayer.support

import io.lightcone.relayer.actors._
import io.lightcone.persistence.dals._
import io.lightcone.persistence._
import org.scalatest.BeforeAndAfterAll

trait DatabaseModuleSupport extends BeforeAndAfterAll {
  me: CommonSpec =>

  implicit val dbConfig = dbConfig1

  implicit val tokenMetadataDal = new TokenMetadataDalImpl
  implicit val orderDal = new OrderDalImpl
  implicit val tradeDal = new TradeDalImpl
  implicit val ringDal = new RingDalImpl
  implicit val blockDal = new BlockDalImpl
  implicit val settlementTxDal = new SettlementTxDalImpl
  implicit val marketMetadataDal = new MarketMetadataDalImpl()
  implicit val missingBlocksRecordDal = new MissingBlocksRecordDalImpl()
  implicit val requestJobDal = new RequestJobDalImpl()
  implicit val CMCTickersInUsdDal = new CMCTickersInUsdDalImpl()
  implicit val currencyRateDal = new CurrencyRateDalImpl()
  implicit val orderService = new OrderServiceImpl
  implicit val tradeService = new TradeServiceImpl
  implicit val ringService = new RingServiceImpl
  implicit val blockService = new BlockServiceImpl()
  implicit val settlementTxService = new SettlementTxServiceImpl

  implicit val ohlcDataDal =
    new OHLCDataDalImpl()(ec = ec, dbConfig = dbConfig_postgre)
  implicit val ohlcDataService =
    new OHLCDataServiceImpl()(ohlcDataDal = ohlcDataDal, ec = ec)

  implicit val dbModule = new DatabaseModule(
    tokenMetadataDal,
    orderDal,
    tradeDal,
    ringDal,
    blockDal,
    settlementTxDal,
    marketMetadataDal,
    missingBlocksRecordDal,
    ohlcDataDal,
    requestJobDal,
    CMCTickersInUsdDal,
    currencyRateDal,
    orderService,
    tradeService,
    ringService,
    blockService,
    settlementTxService,
    ohlcDataService
  )

  dbModule.dropTables()
  dbModule.createTables()

  tokenMetadataDal.saveTokens(TOKENS)
  marketMetadataDal.saveMarkets(MARKETS)

  actors.add(DatabaseQueryActor.name, DatabaseQueryActor.start)
}
