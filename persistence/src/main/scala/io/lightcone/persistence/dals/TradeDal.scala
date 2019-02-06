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

package io.lightcone.persistence.dals

import com.google.inject.Inject
import com.google.inject.name.Named
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException
import com.typesafe.scalalogging.Logger
import io.lightcone.lib._
import io.lightcone.persistence.base._
import io.lightcone.proto._
import io.lightcone.core._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import slick.lifted.Query
import scala.concurrent._
import scala.util.{Failure, Success}

trait TradeDal extends BaseDalImpl[TradeTable, Trade] {
  def saveTrade(trade: Trade): Future[ErrorCode]
  def saveTrades(trades: Seq[Trade]): Future[Seq[ErrorCode]]
  def getTrades(request: GetTrades.Req): Future[Seq[Trade]]
  def countTrades(request: GetTrades.Req): Future[Int]
  def obsolete(height: Long): Future[Unit]
}