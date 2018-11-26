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

package org.loopring.lightcone.lib.abi

import org.loopring.lightcone.lib.data._

class CancelABI(jsonStr: String) extends AbiWrap(jsonStr) {

  val EN_ORDERS_CANCELLED = "OrdersCancelled"
  val EN_ALL_ORDERS_CANCELLED_FOR_TRADING_PAIR = "AllOrdersCancelledForTradingPair"
  val EN_ALL_ORDERS_CANCELLED = "AllOrdersCancelled"
  val EN_ORDERS_CANCELLED_BY_BROKER = "OrdersCancelledByBroker"
  val EN_ALL_ORDERS_CANCELLED_FOR_TRADING_PAIR_BY_BROKER = "AllOrdersCancelledForTradingPairByBroker"
  val EN_ALL_ORDERS_CANCELLED_BY_BROKER = "AllOrdersCancelledByBroker"

  val FN_CANCEL_ORDERS = "cancelOrders"
  val FN_CANCEL_ALL_ORDERS_FOR_TRADING_PAIR = "cancelAllOrdersForTradingPair"
  val FN_CANCEL_ALL_ORDERS = "cancelAllOrders"
  val FN_CANCEL_ALL_ORDERS_FOR_TRADING_PAIR_OF_OWNER = "cancelAllOrdersForTradingPairOfOwner"
  val FN_CANCEL_ALL_ORDERS_OF_OWNER = "cancelAllOrdersOfOwner"

  def decodeAndAssemble(tx: Transaction): Option[Any] = {
    val result = decode(tx.input)
    val data = result.name match {
      case FN_CANCEL_ORDERS ⇒
        assembleCancelOrdersFunction(result.list, tx.from)

      case FN_CANCEL_ALL_ORDERS_FOR_TRADING_PAIR ⇒
        assembleCancelAllOrdersForTradingPairFunction(result.list, tx.from)

      case FN_CANCEL_ALL_ORDERS ⇒
        assembleCancelAllOrdersFunction(result.list, tx.from)

      case FN_CANCEL_ALL_ORDERS_FOR_TRADING_PAIR_OF_OWNER ⇒
        assembleCancelAllOrdersForTradingPairOfOwnerFunction(result.list, tx.from)

      case FN_CANCEL_ALL_ORDERS_OF_OWNER ⇒
        assembleCancelAllOrdersOfOwnerFunction(result.list, tx.from)

      case _ ⇒ None
    }
    Option(data)
  }

  def decodeAndAssemble(tx: Transaction, log: TransactionLog): Option[Any] = {
    val result = decode(log)
    val data = result.name match {
      case EN_ORDERS_CANCELLED ⇒
        assembleOrdersCancelledEvent(result.list)

      case EN_ALL_ORDERS_CANCELLED_FOR_TRADING_PAIR ⇒
        assembleAllOrdersCancelledForTradingPairEvent(result.list)

      case EN_ALL_ORDERS_CANCELLED ⇒
        assembleAllOrdersCancelledEvent(result.list)

      case EN_ORDERS_CANCELLED_BY_BROKER ⇒
        assembleOrdersCancelledByBrokerEvent(result.list)

      case EN_ALL_ORDERS_CANCELLED_FOR_TRADING_PAIR_BY_BROKER ⇒
        assembleAllOrdersCancelledForTradingPairByBrokerEvent(result.list)

      case EN_ALL_ORDERS_CANCELLED_BY_BROKER ⇒
        assembleAllOrdersCancelledByBrokerEvent(result.list)

      case _ ⇒ None
    }
    Some(data)
  }

  // msg.sender needs to be the broker of the orders you want to cancel
  private[lib] def assembleCancelOrdersFunction(list: Seq[Any], from: String) = {
    assert(list.length == 1, "length of CancelOrders Function invalid")

    val orders = list(0) match {
      case x: Array[Object] ⇒ x.map(scalaAny2Hex(_))
      case _                ⇒ throw new Exception("convert array error")
    }

    OrdersCancelled(
      broker = from,
      orderhashs = orders
    )
  }

  // msg.sender needs to be the broker of the orders you want to cancel.
  private[lib] def assembleCancelAllOrdersForTradingPairFunction(list: Seq[Any], from: String) = {
    assert(list.length == 3, "length of CancelAllOrdersForTradingPair Function invalid")

    AllOrdersCancelledForTradingPair(
      broker = scalaAny2Hex(from),
      token1 = scalaAny2Hex(list(0)),
      token2 = scalaAny2Hex(list(1)),
      cutoff = scalaAny2Bigint(list(2))
    )
  }

  // msg.sender is the broker of the orders for which the cutoff is set
  private[lib] def assembleCancelAllOrdersFunction(list: Seq[Any], from: String) = {
    assert(list.length == 1, "length of CancelAllOrders Function invalid")

    AllOrdersCancelled(
      broker = scalaAny2Hex(from),
      cutoff = scalaAny2Bigint(list(0))
    )
  }

  // msg.sender is the broker of the orders for which the cutoff is set
  private[lib] def assembleCancelAllOrdersForTradingPairOfOwnerFunction(list: Seq[Any], from: String) = {
    assert(list.length == 4, "length of CancelAllOrdersForTradingPairOfOwner Function invalid")

    AllOrdersCancelledForTradingPairByBroker(
      broker = from,
      owner = scalaAny2Hex(list(0)),
      token1 = scalaAny2Hex(list(1)),
      token2 = scalaAny2Hex(list(2)),
      cutoff = scalaAny2Bigint(list(3))
    )
  }

  // msg.sender is the broker of the orders for which the cutoff is set
  private[lib] def assembleCancelAllOrdersOfOwnerFunction(list: Seq[Any], from: String) = {
    assert(list.length == 2, "length of CancelAllOrdersOfOwner Function invalid")

    AllOrdersCancelledByBroker(
      broker = from,
      owner = scalaAny2Hex(list(0)),
      cutoff = scalaAny2Bigint(list(1))
    )
  }

  private[lib] def assembleOrdersCancelledEvent(list: Seq[Any]) = {
    assert(list.length == 2, "length of OrdersCancelled event invalid")

    val orders = list(1) match {
      case x: Array[Object] ⇒ x.map(scalaAny2Hex(_))
      case _                ⇒ throw new Exception("convert array error")
    }

    OrdersCancelled(
      broker = scalaAny2Hex(list(0)),
      orderhashs = orders
    )
  }

  private[lib] def assembleAllOrdersCancelledForTradingPairEvent(list: Seq[Any]) = {
    assert(list.length == 4, "length of AllOrdersCancelledForTradingPair event invalid")

    AllOrdersCancelledForTradingPair(
      broker = scalaAny2Hex(list(0)),
      token1 = scalaAny2Hex(list(1)),
      token2 = scalaAny2Hex(list(2)),
      cutoff = scalaAny2Bigint(list(3))
    )
  }

  private[lib] def assembleAllOrdersCancelledEvent(list: Seq[Any]) = {
    assert(list.length == 2, "length of AllOrdersCancelled event invalid")

    AllOrdersCancelled(
      broker = scalaAny2Hex(list(0)),
      cutoff = scalaAny2Bigint(list(1))
    )
  }

  private[lib] def assembleOrdersCancelledByBrokerEvent(list: Seq[Any]) = {
    assert(list.length == 3, "length of OrdersCancelledByBroker event invalid")

    val orders = list(1) match {
      case x: Array[Object] ⇒ x.map(scalaAny2Hex(_))
      case _                ⇒ throw new Exception("convert array error")
    }

    OrdersCancelledByBroker(
      broker = scalaAny2Hex(list(0)),
      owner = scalaAny2Hex(list(1)),
      orderhashs = orders
    )
  }

  private[lib] def assembleAllOrdersCancelledForTradingPairByBrokerEvent(list: Seq[Any]) = {
    assert(list.length == 5, "length of AllOrdersCancelledForTradingPairByBroker event invalid")

    AllOrdersCancelledForTradingPairByBroker(
      broker = scalaAny2Hex(list(0)),
      owner = scalaAny2Hex(list(1)),
      token1 = scalaAny2Hex(list(2)),
      token2 = scalaAny2Hex(list(3)),
      cutoff = scalaAny2Bigint(list(4))
    )
  }

  private[lib] def assembleAllOrdersCancelledByBrokerEvent(list: Seq[Any]) = {
    assert(list.length == 3, "length of AllOrdersCancelledByBroker event invalid")

    AllOrdersCancelledByBroker(
      broker = scalaAny2Hex(list(0)),
      owner = scalaAny2Hex(list(1)),
      cutoff = scalaAny2Bigint(list(2))
    )
  }

}
