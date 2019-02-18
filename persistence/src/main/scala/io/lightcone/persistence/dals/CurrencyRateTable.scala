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

import io.lightcone.persistence._
import io.lightcone.persistence.base._
import slick.jdbc.MySQLProfile.api._

class CurrencyRateTable(tag: Tag)
    extends BaseTable[CurrencyRate](tag, "T_CURRENCY_RATE") {

  def id = batchId.toString()
  def rate = column[Double]("rate")
  def time = column[Long]("time")
  def batchId = column[Int]("batch_id", O.PrimaryKey)

  def * =
    (
      rate,
      time,
      batchId
    ) <> ((CurrencyRate.apply _).tupled, CurrencyRate.unapply)
}
