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

import io.lightcone.core.TokenInfo
import io.lightcone.relayer.actors._
import io.lightcone.persistence.dals._
import io.lightcone.persistence._
import io.lightcone.relayer.DatabaseConfigManager
import org.scalatest.BeforeAndAfterAll

trait DatabaseModuleSupport extends BeforeAndAfterAll {
  me: CommonSpec =>

  val dbConfigManager = new DatabaseConfigManager(config)

  implicit val tokenMetadataDal = new TokenMetadataDalImpl()(
    ec,
    dbConfigManager.getDatabaseConfig("db.dbconfig-dal-token-metadata"),
    timeProvider
  )
  implicit val tokenInfoDal = new TokenInfoDalImpl()(
    ec,
    dbConfigManager.getDatabaseConfig("db.dbconfig-dal-token-info"),
    timeProvider
  )
  implicit val orderDal = new OrderDalImpl()(
    ec,
    dbConfigManager.getDatabaseConfig("db.dbconfig-dal-order"),
    timeProvider
  )
  implicit val fillDal = new FillDalImpl()(
    ec,
    dbConfigManager.getDatabaseConfig("db.dbconfig-dal-ring"),
    timeProvider
  )
  implicit val ringDal = new RingDalImpl()(
    ec,
    dbConfigManager.getDatabaseConfig("db.dbconfig-dal-token-balance"),
    timeProvider
  )
  implicit val blockDal = new BlockDalImpl()(
    ec,
    dbConfigManager.getDatabaseConfig("db.dbconfig-dal-block")
  )
  implicit val settlementTxDal = new SettlementTxDalImpl()(
    ec,
    dbConfigManager.getDatabaseConfig("db.dbconfig-dal-settlement-tx")
  )
  implicit val marketMetadataDal = new MarketMetadataDalImpl()(
    ec,
    dbConfigManager.getDatabaseConfig("db.dbconfig-dal-market-metadata"),
    timeProvider
  )
  implicit val missingBlocksRecordDal = new MissingBlocksRecordDalImpl()(
    ec,
    dbConfigManager.getDatabaseConfig("db.dbconfig-dal-missing-blocks-record")
  )
  implicit val tokenTickerRecordDal = new TokenTickerRecordDalImpl()(
    ec,
    dbConfigManager.getDatabaseConfig("db.dbconfig-dal-token-ticker-record")
  )
  implicit val cmcCrawlerConfigForTokenDal =
    new CMCCrawlerConfigForTokenDalImpl()(
      ec,
      dbConfigManager.getDatabaseConfig("db.dbconfig-dal-cmc-ticker-config")
    )
  implicit val orderService = new OrderServiceImpl()
  implicit val blockService = new BlockServiceImpl()
  implicit val settlementTxService = new SettlementTxServiceImpl()

  implicit val ohlcDataDal =
    new OHLCDataDalImpl()(
      ec = ec,
      dbConfig = dbConfigManager.getDatabaseConfig("db.dbconfig-dal-ohlc-data")
    )
  implicit val ohlcDataService = new OHLCDataServiceImpl()

  implicit val dbModule = new DatabaseModule(
    tokenMetadataDal,
    tokenInfoDal,
    orderDal,
    fillDal,
    ringDal,
    blockDal,
    settlementTxDal,
    marketMetadataDal,
    missingBlocksRecordDal,
    tokenTickerRecordDal,
    cmcCrawlerConfigForTokenDal,
    ohlcDataDal,
    orderService,
    blockService,
    settlementTxService,
    ohlcDataService
  )

  dbModule.dropTables()
  dbModule.createTables()

  tokenMetadataDal.saveTokenMetadatas(TOKENS)
  tokenInfoDal.saveTokenInfos(TOKENS.map { t =>
    TokenInfo(t.symbol)
  })
  cmcCrawlerConfigForTokenDal.saveConfigs(TOKEN_SLUGS_SYMBOLS.map { t =>
    CMCCrawlerConfigForToken(t._1, t._2)
  })
  marketMetadataDal.saveMarkets(MARKETS)

  actors.add(DatabaseQueryActor.name, DatabaseQueryActor.start)
}
