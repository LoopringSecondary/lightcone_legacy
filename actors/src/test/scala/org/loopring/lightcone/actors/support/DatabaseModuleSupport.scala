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

package org.loopring.lightcone.actors.support

import org.loopring.lightcone.actors.core.DatabaseQueryActor
import org.loopring.lightcone.persistence._
import org.loopring.lightcone.persistence.dals._
import org.loopring.lightcone.persistence.service._
import org.scalatest.BeforeAndAfterAll

trait DatabaseModuleSupport extends BeforeAndAfterAll {
  my: CommonSpec =>

  implicit val dbConfig = dbConfig1

  implicit val tokenMetadataDal = new TokenMetadataDalImpl
  implicit val orderDal = new OrderDalImpl
  implicit val tradeDal = new TradeDalImpl
  implicit val blockDal = new BlockDalImpl
  implicit val settlementTxDal = new SettlementTxDalImpl
  implicit val orderStatusMonitorDal =
    new OrderStatusMonitorDalImpl
  implicit val marketMetadataDal = new MarketMetadataDalImpl()
  implicit val orderService = new OrderServiceImpl
  implicit val orderStatusMonitorService =
    new OrderStatusMonitorServiceImpl
  implicit val tradeService = new TradeServiceImpl
  implicit val blockService = new BlockServiceImpl()
  implicit val settlementTxService =
    new SettlementTxServiceImpl

  implicit val dbModule = new DatabaseModule(
    tokenMetadataDal,
    orderDal,
    tradeDal,
    blockDal,
    settlementTxDal,
    orderStatusMonitorDal,
    marketMetadataDal,
    orderService,
    orderStatusMonitorService,
    tradeService,
    blockService,
    settlementTxService
  )

  dbModule.dropTables()
  dbModule.createTables()
  actors.add(DatabaseQueryActor.name, DatabaseQueryActor.start)

}
