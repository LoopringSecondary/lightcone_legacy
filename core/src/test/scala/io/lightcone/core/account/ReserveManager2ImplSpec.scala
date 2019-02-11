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

package io.lightcone.core

import io.lightcone.core.testing._

class ReserveManager2ImplSpec extends CommonSpec {

  val token = "ABC"
  var manager: ReserveManager2Impl = _

  implicit def int2bigInt(i: Int) = BigInt(i)

  implicit val reh = new ReserveEventHandler {

    def onTokenReservedForOrder(
        orderId: String,
        token: String,
        amount: BigInt
      ) = {
      // println(s"reserved $amount@$token for order: $orderId")
    }
  }

  override def beforeEach(): Unit = {
    manager = new ReserveManager2Impl(token)
  }

  "ReserveManager2Impl" should "not reserve in 0 balance or allowance" in {
    var result = manager.reserve("order1", 100)
    result should be(Set("order1"))
    manager.getAccountInfo should be(AccountInfo(token, 0, 0, 0, 0, 0))

    // Set balance big enought, but allowance a bit smaller than 100
    manager.setBalanceAndAllowance(100, 99)
    result = manager.reserve("order1", 100)
    result should be(Set("order1"))
    manager.getAccountInfo should be(AccountInfo(token, 100, 99, 100, 99, 0))

    // Set allowance big enought, but balance a bit smaller than 100
    manager.setBalanceAndAllowance(99, 100)
    result = manager.reserve("order1", 100)
    result should be(Set("order1"))
    manager.getAccountInfo should be(AccountInfo(token, 99, 100, 99, 100, 0))
  }

  "ReserveManager2Impl" should "reserve multiple orders if balance/allowance are both suffcient and these orders can be released" in {
    manager.setBalanceAndAllowance(100, 110)

    var result = manager.reserve("order1", 50)
    result should be(Set.empty[String])
    manager.getAccountInfo should be(AccountInfo(token, 100, 110, 50, 60, 1))

    result = manager.reserve("order2", 50)
    result should be(Set.empty[String])
    manager.getAccountInfo should be(AccountInfo(token, 100, 110, 0, 10, 2))

    result = manager.release("order1")
    result should be(Set("order1"))
    manager.getAccountInfo should be(AccountInfo(token, 100, 110, 50, 60, 1))

    result = manager.release("order2")
    result should be(Set("order2"))
    manager.getAccountInfo should be(AccountInfo(token, 100, 110, 100, 110, 0))
    manager.getReserves should be(Seq.empty)
  }

  "ReserveManager2Impl" should "reserve 0 amount and succeed" in {
    manager.setBalanceAndAllowance(100, 100)

    var result = manager.reserve("order1", 0)
    result should be(Set.empty[String])
    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 100, 100, 0))
  }

  "ReserveManager2Impl" should "not throw exception when reserve amount < 0 but should indicate the reserve fails" in {
    manager.setBalanceAndAllowance(100, 100)

    var result = manager.reserve("order1", -10)
    result should be(Set("order1"))
    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 100, 100, 0))
  }

  "ReserveManager2Impl" should "release multiple orders in one operation" in {
    manager.setBalanceAndAllowance(100, 100)

    // create 10 orders
    val orderIds = (1 to 10).map("order" + _).toSeq
    orderIds.foreach { orderId =>
      manager.reserve(orderId, 10)
    }
    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 0, 0, 10))

    // release 5 orders first
    var result = manager.release(orderIds.take(5))

    result should be(orderIds.take(5).toSet)
    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 50, 50, 5))

    // release the other five
    result = manager.release(orderIds)
    result should be(orderIds.drop(5).toSet)
    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 100, 100, 0))
    manager.getReserves should be(Seq.empty)
  }

  "a new order" should "kick off one or more old orders only if those old orders will release enought spendable" in {
    manager.setBalanceAndAllowance(100, 100)
    // create 10 orders
    val orderIds = (1 to 10).map("order" + _).toSeq
    orderIds.foreach { orderId =>
      manager.reserve(orderId, 10)
    }
    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 0, 0, 10))

    var result = manager.reserve("order11", 101)
    result should be(Set("order11"))

    result = manager.reserve("order11", 40)
    result should be(orderIds.take(4).toSet)
    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 0, 0, 7))

    // now the reserve is like:
    // List(Reserve(order5,10),
    //      Reserve(order6,10),
    //      Reserve(order7,10),
    //      Reserve(order8,10),
    //      Reserve(order9,10),
    //      Reserve(order10,10),
    //      Reserve(order11,40))
    result = manager.reserve("order12", 70)
    result should be((5 to 11).map("order" + _).toSet)
    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 30, 30, 1))
  }

  "an existing order" should "not reserve if the new size is greater than its origin reserved amount plus all orders prior to this order" in {
    manager.setBalanceAndAllowance(100, 100)

    // create 10 orders
    val orderIds = (1 to 10).map("order" + _).toSeq
    orderIds.foreach { orderId =>
      manager.reserve(orderId, 10)
    }
    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 0, 0, 10))

    info("enlarge the oldest order will make it fail to reserve")
    var result = manager.reserve("order1", 11)
    result should be(Set("order1"))
    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 10, 10, 9))

    info("enlarge the oldest order will make it reserve more")
    result = manager.reserve("order2", 11)
    result should be(Set.empty)
    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 9, 9, 9))

    info("enlarge the newest order will make it fail to reserve")
    result = manager.reserve("order10", 101)
    result should be(Set("order10"))
    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 19, 19, 8))

    // List(Reserve(order2,11), Reserve(order3,10), Reserve(order4,10), Reserve(order5,10),
    //      Reserve(order6,10), Reserve(order7,10), Reserve(order8,10), Reserve(order9,10))

    info("enlarge the newest order will make kick out the oldest orders")
    result = manager.reserve("order9", 50)
    result should be(Set("order2", "order3"))

    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 0, 0, 6))

    // List(Reserve(order4,10), Reserve(order5,10), Reserve(order6,10), Reserve(order7,10),
    //      Reserve(order8,10), Reserve(order9,50))

    result = manager.reserve("order9", 99)
    result should be(Set("order4", "order5", "order6", "order7", "order8"))
    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 1, 1, 1))

    result = manager.reserve("order9", 100)
    result should be(Set.empty[String])
    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 0, 0, 1))

    result = manager.reserve("order9", 101)
    result should be(Set("order9"))
    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 100, 100, 0))
  }

  "setting allowance/balance to larger values" should "not affect existing reserves" in {
    manager.setBalanceAndAllowance(100, 100)

    // create 10 orders
    val orderIds = (1 to 10).map("order" + _).toSeq
    orderIds.foreach { orderId =>
      manager.reserve(orderId, 10)
    }
    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 0, 0, 10))

    var result = manager.setBalanceAndAllowance(101, 101)
    result should be(Set.empty[String])
    manager.getAccountInfo should be(AccountInfo(token, 101, 101, 1, 1, 10))

    result = manager.setBalance(102)
    result should be(Set.empty[String])
    manager.getAccountInfo should be(AccountInfo(token, 102, 101, 2, 1, 10))

    result = manager.setAllowance(102)
    result should be(Set.empty[String])
    manager.getAccountInfo should be(AccountInfo(token, 102, 102, 2, 2, 10))

    result = manager.setBalanceAndAllowance(103, 103)
    result should be(Set.empty[String])
    manager.getAccountInfo should be(AccountInfo(token, 103, 103, 3, 3, 10))
  }

  "setting allowanxe/balance to smaller values" should "drop old orders" in {
    manager.setBalanceAndAllowance(100, 100)

    // create 10 orders
    val orderIds = (1 to 10).map("order" + _).toSeq
    orderIds.foreach { orderId =>
      manager.reserve(orderId, 10)
    }
    manager.getAccountInfo should be(AccountInfo(token, 100, 100, 0, 0, 10))

    var result = manager.setBalance(99)
    result should be(Set("order1"))
    manager.getAccountInfo should be(AccountInfo(token, 99, 100, 9, 10, 9))

    result = manager.setAllowance(90)
    result should be(Set.empty[String])
    manager.getAccountInfo should be(AccountInfo(token, 99, 90, 9, 0, 9))

    result = manager.setAllowance(89)
    result should be(Set("order2"))
    manager.getAccountInfo should be(AccountInfo(token, 99, 89, 19, 9, 8))

    result = manager.setAllowance(0)
    result should be((3 to 10).map("order" + _).toSet)
    manager.getAccountInfo should be(AccountInfo(token, 99, 0, 99, 0, 0))
  }
}
