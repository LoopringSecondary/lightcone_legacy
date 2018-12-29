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

package org.loopring.lightcone.actors.core

import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.validator.OrderHandlerMessageValidator
import akka.pattern._

import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import org.web3j.utils.Numeric

class RingSettlementSpec
    extends CommonSpec("""
                         |akka.cluster.roles=[
                         | "multi_account_manager",
                         | "ethereum_query",
                         | "order_handler",
                         | "gas_price",
                         | "orderbook_manager",
                         | "market_manager"]
                         |""".stripMargin)
    with EthereumSupport
    with MarketManagerSupport
    with MultiAccountManagerSupport
    with OrderGenerateSupport
    with OrderHandleSupport
    with OrderbookManagerSupport
    with JsonrpcSupport
    with HttpSupport {

  def selfConfig = config.getConfig(RingSettlementManagerActor.name)
  def orderHandler = actors.get(OrderHandlerActor.name)

  val users = selfConfig
    .getConfigList("users")
    .asScala
    .map(config ⇒ config.getString("addr") → config.getString("key"))

  "Submit a ring tx " must {
    "tx successfully, order, balance, allowance must be right" in {

      val getBaMethod = "get_balance_and_allowance"
      val submit_order = "submit_order"
      val getOrderBook1 = GetOrderbook.Req(
        0,
        100,
        Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val getBalanceReqs =
        users.unzip._1.map(
          user ⇒
            GetBalanceAndAllowances.Req(
              user,
              tokens =
                Seq(LRC_TOKEN.address, WETH_TOKEN.address, GTO_TOKEN.address)
            )
        )

      val order1 = createRawOrder(owner = users.head._1)(Some(users.head._2))
      val order2 = createRawOrder(
        owner = users(1)._1,
        tokenB = LRC_TOKEN.address,
        tokenS = WETH_TOKEN.address,
        amountB = "10".zeros(18),
        amountS = "1".zeros(18)
      )(Some(users(1)._2))

      val f = for {
        ba1 ← Future.sequence(
          getBalanceReqs.map(
            req ⇒
              singleRequest(req, getBaMethod)
                .mapAs[GetBalanceAndAllowances.Res]
          )
        )
        _ ← singleRequest(
          SubmitOrder.Req(Some(order1)),
          submit_order
        ).mapAs[SubmitOrder.Res]
        _ ← Future {
          Thread.sleep(1000)
        }
        orderbookF1 ← singleRequest(
          getOrderBook1,
          "orderbook"
        ).mapAs[GetOrderbook.Res]
          .map(_.getOrderbook)

        subOrderRes2 ← singleRequest(
          SubmitOrder.Req(Some(order2)),
          submit_order
        ).mapAs[SubmitOrder.Res]

        orderbookF2 ← singleRequest(
          getOrderBook1,
          "orderbook"
        ).mapAs[GetOrderbook.Res]
          .map(_.getOrderbook)

        _ ← Future {
          Thread.sleep(1000 * 10)
        }
        ba2 ← Future.sequence(
          getBalanceReqs.map(
            req ⇒
              singleRequest(req, getBaMethod)
                .mapAs[GetBalanceAndAllowances.Res]
          )
        )
      } yield {
        ba1.foreach(bas ⇒ {
          println("-" * 20 + bas.address + "-" * 20)
          bas.balanceAndAllowanceMap.foreach { ba ⇒
            println(
              ba._1 + "-" * 5 + Numeric
                .toHexStringWithPrefix(
                  Numeric.toBigInt(ba._2.balance.toByteArray)
                ) + "-" * 5 + Numeric
                .toHexStringWithPrefix(
                  Numeric.toBigInt(ba._2.availableBalance.toByteArray)
                )
            )
          }
        })
        println("*" * 50)
        println("*" * 20 + "Buy" + "*" * 20)
        orderbookF1.buys.foreach(
          item ⇒
            println(
              s"${item.amount}" + "-" * 5 + item.price + "-" * 5 + item.total
            )
        )
        println("*" * 20 + "Sell" + "*" * 20)
        orderbookF1.sells.foreach(
          item ⇒
            println(
              s"${item.amount}" + "-" * 5 + item.price + "-" * 5 + item.total
            )
        )
        println("*" * 50)
        println("*" * 20 + "Buy" + "*" * 20)
        orderbookF2.buys.foreach(
          item ⇒
            println(
              s"${item.amount}" + "-" * 5 + item.price + "-" * 5 + item.total
            )
        )
        println("*" * 20 + "Sell" + "*" * 20)
        orderbookF2.sells.foreach(
          item ⇒
            println(
              s"${item.amount}" + "-" * 5 + item.price + "-" * 5 + item.total
            )
        )
        println("*" * 50)
        ba2.foreach(bas ⇒ {
          println("-" * 20 + bas.address + "-" * 20)
          bas.balanceAndAllowanceMap.foreach { ba ⇒
            println(
              ba._1 + "-" * 5 + Numeric
                .toHexStringWithPrefix(
                  Numeric.toBigInt(ba._2.balance.toByteArray)
                ) + "-" * 5 + Numeric
                .toHexStringWithPrefix(
                  Numeric.toBigInt(ba._2.availableBalance.toByteArray)
                )
            )
          }
        })
      }

      Await.result(f, 2 minutes)
    }
  }

}
