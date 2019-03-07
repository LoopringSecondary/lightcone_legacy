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

import io.lightcone.core._
import io.lightcone.persistence.base._
import slick.jdbc.MySQLProfile.api._

class TokenInfoTable(tag: Tag)
    extends BaseTable[TokenInfo](tag, "T_TOKEN_INFO") {

  def id = symbol

  def symbol = column[String]("symbol", O.SqlType("VARCHAR(20)"), O.PrimaryKey)
  def circulatingSupply = column[Double]("circulating_supply")
  def totalSupply = column[Double]("total_supply")
  def maxSupply = column[Double]("max_supply")
  def cmcRank = column[Int]("cmc_rank")
  def icoRateWithEth = column[Double]("ico_rate_with_eth")
  def websiteUrl = column[String]("website_url")
  def updatedAt = column[Long]("updated_at")

  def * =
    (
      symbol,
      circulatingSupply,
      totalSupply,
      maxSupply,
      cmcRank,
      icoRateWithEth,
      websiteUrl,
      updatedAt
    ) <> ((TokenInfo.apply _).tupled, TokenInfo.unapply)
}
