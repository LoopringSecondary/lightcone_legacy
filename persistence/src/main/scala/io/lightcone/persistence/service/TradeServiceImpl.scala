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

package io.lightcone.persistence.service

import com.google.inject.Inject
import com.google.inject.name.Named
import io.lightcone.persistence.dals._
import io.lightcone.core._
import io.lightcone.proto._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import scala.concurrent.{ExecutionContext, Future}

class TradeServiceImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    tradeDal: TradeDal)
    extends TradeService {

  def saveTrade(trade: Trade): Future[ErrorCode] =
    tradeDal.saveTrade(trade)

  def saveTrades(trades: Seq[Trade]) = tradeDal.saveTrades(trades)

  def getTrades(request: GetTrades.Req): Future[Seq[Trade]] =
    tradeDal.getTrades(request)

  def countTrades(request: GetTrades.Req): Future[Int] =
    tradeDal.countTrades(request)

  def obsolete(height: Long): Future[Unit] = tradeDal.obsolete(height)
}
