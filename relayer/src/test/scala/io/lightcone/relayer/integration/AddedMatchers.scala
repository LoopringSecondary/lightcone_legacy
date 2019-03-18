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

package io.lightcone.relayer.integration
import io.lightcone.core.OrderStatus._
import io.lightcone.relayer.data._
import org.scalatest.matchers.{MatchResult, Matcher}

object AddedMatchers {

  def check[T](checkFun: T => Boolean)(implicit m: Manifest[T]) = {
    Matcher { res: T =>
      MatchResult(
        checkFun(res),
        res + " doesn't match",
        res + " matchs"
      )
    }
  }

  def containsInGetOrders(containedOrderHash: String) = {
    def findOrder(res: GetOrders.Res) =
      res.orders.find(_.hash.toLowerCase() == containedOrderHash.toLowerCase())
    Matcher { res: GetOrders.Res =>
      MatchResult(
        findOrder(res).nonEmpty,
        s" ${res} doesn't contains order: ${containedOrderHash}",
        res + " contains it."
      )
    } and
      Matcher { res: GetOrders.Res =>
        MatchResult(
          findOrder(res).get.getState.status == STATUS_SOFT_CANCELLED_BY_USER,
          s"The status of order:${findOrder(res)} in result isn't  STATUS_SOFT_CANCELLED_BY_USER. ",
          "the status matched."
        )
      }
  }

  def orderBookNonEmpty() = {
    Matcher { res: GetOrderbook.Res =>
      MatchResult(
        res.orderbook.nonEmpty,
        s" ${res} of orderBook isEmpty.",
        s"${res} of orderBook nonEmpty."
      )
    }
  }

  def userFillsIsEmpty() = {
    Matcher { res: GetUserFills.Res =>
      MatchResult(
        res.fills.isEmpty,
        s" ${res} of getUserFills nonEmpty.",
        s"${res} of getUserFills isEmpty."
      )
    }
  }

  def marketFillsIsEmpty() = {
    Matcher { res: GetMarketFills.Res =>
      MatchResult(
        res.fills.isEmpty,
        s" ${res} of GetMarketFills nonEmpty.",
        s"${res} of GetMarketFills isEmpty."
      )
    }
  }

}
