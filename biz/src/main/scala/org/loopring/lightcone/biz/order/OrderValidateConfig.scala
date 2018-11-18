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

package org.loopring.lightcone.biz.order

import com.typesafe.config.{ Config, ConfigObject }
import scala.collection.JavaConverters._

object OrderValidateConfig {

  def decodeMinTokenSAmount(config: Config): Map[String, String] = {
    val list: Iterable[ConfigObject] = config.getObjectList("order.validate").asScala
    var rst: Map[String, String] = Map()

    list.foreach { item ⇒
      item.entrySet().forEach { entry ⇒
        rst += (entry.getKey -> entry.getValue.unwrapped().toString)
      }
    }
    rst
  }

  def fromConfig(config: Config) = new OrderValidateConfig(
    config.getLong("order.validate.min_lrc_fee"),
    config.getLong("order.validate.min_lrc_hold"),
    config.getDouble("order.validate.min_split_percentage"),
    config.getDouble("order.validate.max_split_percentage"),
    config.getDouble("order.validate.min_token_s_usd_amount"),
    config.getLong("order.validate.max_valid_since_interval"),
    decodeMinTokenSAmount(config),
    config.getString("order.validate.pow_difficulty")
  )
}

case class OrderValidateConfig(
    minLrcFee: Long,
    minLrcHold: Long,
    minSplitPercentage: Double,
    maxSplitPercentage: Double,
    minTokenSUsdAmount: Double,
    maxValidSinceInterval: Long,
    minTokenSAmount: Map[String, String],
    powDifficulty: String
)
