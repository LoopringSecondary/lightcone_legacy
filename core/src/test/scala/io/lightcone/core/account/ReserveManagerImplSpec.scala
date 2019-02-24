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

class ReserveManagerImplSpec extends CommonSpec {

  implicit val token = "ABC"
  var manager: ReserveManagerImpl = _

  implicit def int2bigInt(i: Int) = BigInt(i)

  var proccssedOrderIds = Seq.empty[(String, BigInt)]

  implicit val reh = new ReserveEventHandler {

    def onTokenReservedForOrder(
        block: Long,
        orderId: String,
        token: String,
        reserved: BigInt
      ) = {
      proccssedOrderIds :+= orderId -> reserved
      // println(s"order: $orderId --> ${reserved.longValue} {$token} ")
    }
  }

  override def beforeEach(): Unit = {
    manager = new ReserveManagerImpl(token)
    proccssedOrderIds = Seq.empty
  }

  "ReserveManagerImpl" should "not reserve in 0 balance or allowance" in {
    var result = manager.reserve("order1", 100)
    result should be(Set("order1"))
    manager.getBalanceOfToken should be(BalanceOfToken(token, 0, 0, 0, 0, 0, 0))
    proccssedOrderIds should be(Nil)
  }

  "ReserveManagerImpl" should "partially reserve and scale up " in {
    // Set balance big enought, but allowance a bit smaller than 100
    manager.setBalanceAndAllowance(10000001L, 110, 80)
    var result = manager.reserve("order1", 100)
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 110, 80, 30, 0, 1, 10000001L)
    )
    proccssedOrderIds should be(Seq("order1" -> 80))

    manager.setBalanceAndAllowance(10000002L, 110, 90)
    result = manager.reserve("order1", 100)
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 110, 90, 20, 0, 1, 10000002L)
    )
    proccssedOrderIds should be(
      Seq("order1" -> 80, "order1" -> 90, "order1" -> 90)
    )

    manager.setBalanceAndAllowance(10000003L, 110, 100)
    result = manager.reserve("order1", 100)
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 110, 100, 10, 0, 1, 10000003L)
    )
    proccssedOrderIds should be(
      Seq(
        "order1" -> 80,
        "order1" -> 90,
        "order1" -> 90,
        "order1" -> 100,
        "order1" -> 100
      )
    )

    manager.setBalanceAndAllowance(10000003L, 110, 110)
    result = manager.reserve("order1", 100)
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 110, 110, 10, 10, 1, 10000003L)
    )
    proccssedOrderIds should be(
      Seq(
        "order1" -> 80,
        "order1" -> 90,
        "order1" -> 90,
        "order1" -> 100,
        "order1" -> 100,
        "order1" -> 100
      )
    )

    manager.setBalanceAndAllowance(10000001L, 100, 110)
    result = manager.reserve("order1", 100)
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 100, 110, 0, 10, 1, 10000001L)
    )
    proccssedOrderIds should be(
      Seq(
        "order1" -> 80,
        "order1" -> 90,
        "order1" -> 90,
        "order1" -> 100,
        "order1" -> 100,
        "order1" -> 100,
        "order1" -> 100
      )
    )

    manager.setBalanceAndAllowance(10000001L, 90, 110)
    result = manager.reserve("order1", 100)
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 90, 110, 0, 20, 1, 10000001L)
    )
    proccssedOrderIds should be(
      Seq(
        "order1" -> 80,
        "order1" -> 90,
        "order1" -> 90,
        "order1" -> 100,
        "order1" -> 100,
        "order1" -> 100,
        "order1" -> 100,
        "order1" -> 90,
        "order1" -> 90
      )
    )

    manager.setBalanceAndAllowance(10000001L, 80, 110)
    result = manager.reserve("order1", 100)
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 80, 110, 0, 30, 1, 10000001L)
    )
    proccssedOrderIds should be(
      Seq(
        "order1" -> 80,
        "order1" -> 90,
        "order1" -> 90,
        "order1" -> 100,
        "order1" -> 100,
        "order1" -> 100,
        "order1" -> 100,
        "order1" -> 90,
        "order1" -> 90,
        "order1" -> 80,
        "order1" -> 80
      )
    )

    manager.setBalanceAndAllowance(10000001L, 0, 110)
    result = manager.reserve("order1", 100)
    result should be(Set("order1"))
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 0, 110, 0, 110, 0, 10000001L)
    )
    proccssedOrderIds should be(
      Seq(
        "order1" -> 80,
        "order1" -> 90,
        "order1" -> 90,
        "order1" -> 100,
        "order1" -> 100,
        "order1" -> 100,
        "order1" -> 100,
        "order1" -> 90,
        "order1" -> 90,
        "order1" -> 80,
        "order1" -> 80
      )
    )
  }

  "ReserveManagerImpl" should "reserve multiple orders if balance/allowance are both suffcient and these orders can be released" in {
    val block = 10000001L
    manager.setBalanceAndAllowance(block, 100, 110)

    var result = manager.reserve("order1", 50)
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 100, 110, 50, 60, 1, block)
    )

    result = manager.reserve("order2", 50)
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 100, 110, 0, 10, 2, block)
    )

    proccssedOrderIds = Seq.empty

    result = manager.release("order1")
    result should be(Set("order1"))
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 100, 110, 50, 60, 1, block)
    )
    proccssedOrderIds should be(Nil)

    result = manager.release("order2")
    result should be(Set("order2"))
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 100, 110, 100, 110, 0, block)
    )
    manager.getReserves should be(Seq.empty)
    proccssedOrderIds should be(Nil)
  }

  "ReserveManagerImpl" should "release multiple orders in one operation" in {
    val block = 10000001L
    manager.setBalanceAndAllowance(block, 95, 95)

    // create 10 orders
    val orderIds = (1 to 10).map("order" + _).toSeq
    orderIds.foreach { orderId =>
      manager.reserve(orderId, 10)
    }
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 95, 95, 0, 0, 10, block)
    )

    // release 5 orders first
    proccssedOrderIds = Seq.empty
    var result = manager.release(orderIds.take(5).toSet)

    result should be(orderIds.take(5).toSet)
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 95, 95, 45, 45, 5, block)
    )
    proccssedOrderIds should be(Seq("order10" -> 10))
    proccssedOrderIds = Seq.empty

    // release the other five
    result = manager.release(orderIds.toSet)
    result should be(orderIds.drop(5).toSet)
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 95, 95, 95, 95, 0, block)
    )
    manager.getReserves should be(Seq.empty)
    proccssedOrderIds should be(Nil)
  }

  "a new order" should "NOT kick off old orders" in {
    val block = 10000001L
    manager.setBalanceAndAllowance(block, 100, 100)
    // create 10 orders
    val orderIds = (1 to 9).map("order" + _).toSeq
    orderIds.foreach { orderId =>
      manager.reserve(orderId, 10)
    }
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 100, 100, 10, 10, 9, block)
    )

    proccssedOrderIds = Seq.empty
    var result = manager.reserve("order10", 101)
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 100, 100, 0, 0, 10, block)
    )
    proccssedOrderIds should be(Seq("order10" -> 10))

    proccssedOrderIds = Seq.empty
    result = manager.reserve("order11", 40)
    result should be(Set("order11"))
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 100, 100, 0, 0, 10, block)
    )
    proccssedOrderIds should be(Nil)
  }

  "an existing order" should "reserve as the last item if its new size is greater" in {
    val block = 10000001L
    manager.setBalanceAndAllowance(block, 100, 100)

    // create 10 orders
    val orderIds = (1 to 10).map("order" + _).toSeq
    orderIds.foreach { orderId =>
      manager.reserve(orderId, 10)
    }
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 100, 100, 0, 0, 10, block)
    )

    var result = manager.reserve("order2", 11) // > 10
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 100, 100, 0, 0, 10, block)
    )

    result = manager.setBalanceAndAllowance(block, 90, 90)
    result should be(Set("order2"))

    result = manager.setBalanceAndAllowance(block + 1, 11, 11)
    result = manager.setBalanceAndAllowance(block + 2, 10, 10)
    result should be(Set("order3"))
  }

  "an existing order" should "reserve in place if its new size remains the same" in {
    val block = 10000001L
    manager.setBalanceAndAllowance(block, 100, 100)

    // create 10 orders
    val orderIds = (1 to 10).map("order" + _).toSeq
    orderIds.foreach { orderId =>
      manager.reserve(orderId, 10)
    }
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 100, 100, 0, 0, 10, block)
    )

    var result = manager.reserve("order2", 10) // == 10
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 100, 100, 0, 0, 10, block)
    )

    result = manager.setBalanceAndAllowance(block + 1, 90, 90)
    result should be(Set("order10"))

    result = manager.setBalanceAndAllowance(block + 2, 11, 11)
    result = manager.setBalanceAndAllowance(block + 3, 10, 10)
    result should be(Set("order2"))
  }

  "an existing order" should "reserve in place if its new size remains the smaller" in {
    val block = 10000001L
    manager.setBalanceAndAllowance(block, 100, 100)

    // create 10 orders
    val orderIds = (1 to 10).map("order" + _).toSeq
    orderIds.foreach { orderId =>
      manager.reserve(orderId, 10)
    }
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 100, 100, 0, 0, 10, block)
    )

    var result = manager.reserve("order2", 9) // M 10
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 100, 100, 1, 1, 10, block)
    )

    result = manager.setBalanceAndAllowance(block + 1, 89, 89)
    result should be(Set("order10"))

    result = manager.setBalanceAndAllowance(block + 2, 11, 11)
    result = manager.setBalanceAndAllowance(block + 3, 10, 10)
    result should be(Set("order2"))
  }

  "setting allowance/balance to larger values" should "NOT affect existing reserves" in {
    val block = 10000001L
    manager.setBalanceAndAllowance(block, 100, 100)

    // create 10 orders
    val orderIds = (1 to 10).map("order" + _).toSeq
    orderIds.foreach { orderId =>
      manager.reserve(orderId, 10)
    }
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 100, 100, 0, 0, 10, block)
    )

    var result = manager.setBalanceAndAllowance(block + 1, 101, 101)
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 101, 101, 1, 1, 10, block + 1)
    )

    result = manager.setBalance(block + 2, 102)
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 102, 101, 2, 1, 10, block + 2)
    )

    result = manager.setAllowance(block - 1, 102)
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 102, 102, 2, 2, 10, block - 1)
    )

    result = manager.setBalanceAndAllowance(block + 2, 103, 103)
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 103, 103, 3, 3, 10, block + 2)
    )
  }

  "setting allowanxe/balance to smaller values" should "drop newer orders" in {
    val block = 1L
    manager.setBalanceAndAllowance(block, 100, 100)

    // create 10 orders
    val orderIds = (1 to 10).map("order" + _).toSeq
    orderIds.foreach { orderId =>
      manager.reserve(orderId, 10)
    }
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 100, 100, 0, 0, 10, block)
    )

    proccssedOrderIds = Seq.empty
    var result = manager.setBalance(block + 1, 99)
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 99, 100, 0, 1, 10, block + 1)
    )
    proccssedOrderIds should be(Seq("order10" -> 9))

    result = manager.setAllowance(block + 2, 90)
    result should be(Set("order10"))
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 99, 90, 9, 0, 9, block + 2)
    )
    proccssedOrderIds should be(Seq("order10" -> 9))

    result = manager.setAllowance(block + 3, 89)
    result should be(Set.empty[String])
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 99, 89, 10, 0, 9, block + 3)
    )
    proccssedOrderIds should be(Seq("order10" -> 9, "order9" -> 9))

    proccssedOrderIds = Seq.empty
    result = manager.setAllowance(block + 4, 0)
    result should be((1 to 9).map("order" + _).toSet)
    manager.getBalanceOfToken should be(
      BalanceOfToken(token, 99, 0, 99, 0, 0, block + 4)
    )
    proccssedOrderIds should be(Nil)
  }
}
