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

import io.lightcone.core.ErrorCode
import io.lightcone.persistence.TokenTickerRecord
import io.lightcone.persistence.base._
import scala.concurrent._

trait TokenTickerRecordDal
    extends BaseDalImpl[TokenTickerRecordTable, TokenTickerRecord] {

  def saveTickers(tickers: Seq[TokenTickerRecord]): Future[ErrorCode]

  def getLastTicker(): Future[Option[Long]]

  def getTickers(timestamp: Long): Future[Seq[TokenTickerRecord]]

  def countTickers(timestamp: Long): Future[Int]

  def getTickers(
      timestamp: Long,
      tokenSymbols: Seq[String]
    ): Future[Seq[TokenTickerRecord]]

  def setValid(timestamp: Long): Future[ErrorCode]
}
