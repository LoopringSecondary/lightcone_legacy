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

package io.lightcone.ethereum.abi

import scala.io.Source
import org.ethereum.solidity.Abi
import org.web3j.utils.Numeric
import scala.annotation.meta.field

class OrderCancellerAbi(abiJson: String) extends AbiWrap(abiJson) {

  val cancelAllOrdersForTradingPair =
    CancelAllOrdersForTradingPairFunction(
      abi.findFunction(searchByName(CancelAllOrdersForTradingPairFunction.name))
    )

  val cancelAllOrdersForTradingPairOfOwner =
    CancelAllOrdersForTradingPairOfOwnerFunction(
      abi.findFunction(
        searchByName(CancelAllOrdersForTradingPairOfOwnerFunction.name)
      )
    )

  val cancelAllOrdersOfOwner =
    CancelAllOrdersOfOwnerFunction(
      abi.findFunction(searchByName(CancelAllOrdersOfOwnerFunction.name))
    )

  val cancelOrders = CancelOrdersFunction(
    abi.findFunction(searchByName(CancelOrdersFunction.name))
  )

  val cancelAllOrders = CancelAllOrdersFunction(
    abi.findFunction(searchByName(CancelAllOrdersFunction.name))
  )

  val ordersCancelledEvent = OrdersCancelledEvent(
    abi.findEvent(searchByName(OrdersCancelledEvent.name))
  )

  val allOrdersCancelledForTradingPairEvent =
    AllOrdersCancelledForTradingPairEvent(
      abi.findEvent(searchByName(AllOrdersCancelledForTradingPairEvent.name))
    )

  val allOrdersCancelledEvent = AllOrdersCancelledEvent(
    abi.findEvent(searchByName(AllOrdersCancelledEvent.name))
  )

  val allOrdersCancelledForTradingPairByBrokerEvent =
    AllOrdersCancelledForTradingPairByBrokerEvent(
      abi.findEvent(
        searchByName(AllOrdersCancelledForTradingPairByBrokerEvent.name)
      )
    )

  val allOrdersCancelledByBrokerEvent = AllOrdersCancelledByBrokerEvent(
    abi.findEvent(searchByName(AllOrdersCancelledByBrokerEvent.name))
  )

  def unpackEvent(
      data: String,
      topics: Array[String]
    ): Option[Any] = {

    try {
      val event: Abi.Event = abi.findEvent(
        searchBySignature(
          Numeric.hexStringToByteArray(topics.headOption.getOrElse(""))
        )
      )

      event match {
        case _: Abi.Event =>
          event.name match {
            case OrdersCancelledEvent.name =>
              ordersCancelledEvent.unpack(data, topics)
            case AllOrdersCancelledForTradingPairEvent.name =>
              allOrdersCancelledForTradingPairEvent.unpack(data, topics)
            case AllOrdersCancelledEvent.name =>
              allOrdersCancelledEvent.unpack(data, topics)
            case AllOrdersCancelledForTradingPairByBrokerEvent.name =>
              allOrdersCancelledForTradingPairByBrokerEvent.unpack(data, topics)
            case AllOrdersCancelledByBrokerEvent.name =>
              allOrdersCancelledByBrokerEvent.unpack(data, topics)
            case _ => None
          }
        case _ => None
      }
    } catch {
      case _: Throwable => None
    }
  }

  override def unpackFunctionInput(data: String): Option[Any] = {
    try {
      val funSig =
        Numeric.hexStringToByteArray(
          Numeric.cleanHexPrefix(data).substring(0, 8)
        )
      val func = abi.findFunction(searchBySignature(funSig))
      func match {
        case _: Abi.Function =>
          func.name match {
            case CancelAllOrdersForTradingPairFunction.name =>
              cancelAllOrdersForTradingPair.unpackInput(data)
            case CancelAllOrdersForTradingPairOfOwnerFunction.name =>
              cancelAllOrdersForTradingPairOfOwner.unpackInput(data)
            case CancelAllOrdersOfOwnerFunction.name =>
              cancelAllOrdersOfOwner.unpackInput(data)
            case CancelOrdersFunction.name =>
              cancelOrders.unpackInput(data)
            case CancelAllOrdersFunction.name =>
              cancelAllOrders.unpackInput(data)
            case _ => None
          }
        case _ => None
      }
    } catch {
      case _: Throwable => None
    }
  }
}

object OrderCancellerAbi {

  val jsonStr: String =
    Source.fromResource("version2.0/IOrderCanceller.abi").mkString
  def apply(abiJson: String): OrderCancellerAbi = new OrderCancellerAbi(abiJson)

  def apply(): OrderCancellerAbi = new OrderCancellerAbi(jsonStr)
}

class CancelAllOrdersForTradingPairFunction(val entry: Abi.Function)
    extends AbiFunction[
      CancelAllOrdersForTradingPairFunction.Params,
      CancelAllOrdersForTradingPairFunction.Result
    ] {}

object CancelAllOrdersForTradingPairFunction {
  val name = "cancelAllOrdersForTradingPair"

  case class Params(
      @(ContractAnnotation @field)("token1", 0) token1: String,
      @(ContractAnnotation @field)("token2", 1) token2: String,
      @(ContractAnnotation @field)("cutoff", 2) cutoff: BigInt)

  case class Result()

  def apply(entry: Abi.Function): CancelAllOrdersForTradingPairFunction =
    new CancelAllOrdersForTradingPairFunction(entry)
}

class CancelAllOrdersForTradingPairOfOwnerFunction(val entry: Abi.Function)
    extends AbiFunction[
      CancelAllOrdersForTradingPairOfOwnerFunction.Params,
      CancelAllOrdersForTradingPairOfOwnerFunction.Result
    ] {}

object CancelAllOrdersForTradingPairOfOwnerFunction {
  val name = "cancelAllOrdersForTradingPairOfOwner"

  case class Params(
      @(ContractAnnotation @field)("owner", 0) owner: String,
      @(ContractAnnotation @field)("token1", 1) token1: String,
      @(ContractAnnotation @field)("token2", 2) token2: String,
      @(ContractAnnotation @field)("cutoff", 3) cutoff: BigInt)

  case class Result()

  def apply(entry: Abi.Function): CancelAllOrdersForTradingPairOfOwnerFunction =
    new CancelAllOrdersForTradingPairOfOwnerFunction(entry)
}

class CancelAllOrdersOfOwnerFunction(val entry: Abi.Function)
    extends AbiFunction[
      CancelAllOrdersOfOwnerFunction.Params,
      CancelAllOrdersOfOwnerFunction.Result
    ]

object CancelAllOrdersOfOwnerFunction {

  val name = "cancelAllOrdersOfOwner"

  case class Params(
      @(ContractAnnotation @field)("owner", 0) owner: String,
      @(ContractAnnotation @field)("cutoff", 1) cutoff: BigInt)

  case class Result()

  def apply(entry: Abi.Function): CancelAllOrdersOfOwnerFunction =
    new CancelAllOrdersOfOwnerFunction(entry)
}

class CancelOrdersFunction(val entry: Abi.Function)
    extends AbiFunction[
      CancelOrdersFunction.Params,
      CancelOrdersFunction.Result
    ]

object CancelOrdersFunction {
  val name = "cancelOrders"

  case class Params(
      @(ContractAnnotation @field)("orderHashes", 0) orderHashes: Array[Byte])

  case class Result()

  def apply(entry: Abi.Function): CancelOrdersFunction =
    new CancelOrdersFunction(entry)
}

class CancelAllOrdersFunction(val entry: Abi.Function)
    extends AbiFunction[
      CancelAllOrdersFunction.Params,
      CancelAllOrdersFunction.Params
    ]

object CancelAllOrdersFunction {
  val name = "cancelAllOrders"

  case class Params(@(ContractAnnotation @field)("cutoff", 0) cutoff: BigInt)

  case class Result()

  def apply(entry: Abi.Function): CancelAllOrdersFunction =
    new CancelAllOrdersFunction(entry)
}

class OrdersCancelledEvent(val entry: Abi.Event)
    extends AbiEvent[OrdersCancelledEvent.Result]

object OrdersCancelledEvent {
  val name = "OrdersCancelled"
  case class Result(
      @(ContractAnnotation @field)("address", 0) address: String,
      @(ContractAnnotation @field)("_orderHashes",
          1) _orderHashes: Array[String])

  def apply(entry: Abi.Event): OrdersCancelledEvent =
    new OrdersCancelledEvent(entry)
}

class AllOrdersCancelledForTradingPairEvent(val entry: Abi.Event)
    extends AbiEvent[AllOrdersCancelledForTradingPairEvent.Result]

object AllOrdersCancelledForTradingPairEvent {

  val name = "AllOrdersCancelledForTradingPair"

  case class Result(
      @(ContractAnnotation @field)("_broker", 0) _broker: String,
      @(ContractAnnotation @field)("_token1", 1) _token1: String,
      @(ContractAnnotation @field)("_token2", 2) _token2: String,
      @(ContractAnnotation @field)("_cutoff", 3) _cutoff: BigInt)

  def apply(entry: Abi.Event): AllOrdersCancelledForTradingPairEvent =
    new AllOrdersCancelledForTradingPairEvent(entry)
}

class AllOrdersCancelledEvent(val entry: Abi.Event)
    extends AbiEvent[AllOrdersCancelledEvent.Result]

object AllOrdersCancelledEvent {
  val name = "AllOrdersCancelled"

  case class Result(
      @(ContractAnnotation @field)("_broker", 0) _broker: String,
      @(ContractAnnotation @field)("_cutoff", 1) _cutoff: BigInt)

  def apply(entry: Abi.Event): AllOrdersCancelledEvent =
    new AllOrdersCancelledEvent(entry)
}

class AllOrdersCancelledForTradingPairByBrokerEvent(val entry: Abi.Event)
    extends AbiEvent[AllOrdersCancelledForTradingPairByBrokerEvent.Result]

object AllOrdersCancelledForTradingPairByBrokerEvent {

  val name = "AllOrdersCancelledForTradingPairByBroker"

  case class Result(
      @(ContractAnnotation @field)("_broker", 0) _broker: String,
      @(ContractAnnotation @field)("_owner", 1) _owner: String,
      @(ContractAnnotation @field)("_token1", 2) _token1: String,
      @(ContractAnnotation @field)("_token2", 3) _token2: String,
      @(ContractAnnotation @field)("_cutoff", 4) _cutoff: BigInt)

  def apply(entry: Abi.Event): AllOrdersCancelledForTradingPairByBrokerEvent =
    new AllOrdersCancelledForTradingPairByBrokerEvent(entry)
}

class AllOrdersCancelledByBrokerEvent(val entry: Abi.Event)
    extends AbiEvent[AllOrdersCancelledByBrokerEvent.Result]

object AllOrdersCancelledByBrokerEvent {
  val name = "AllOrdersCancelledByBroker"
  case class Result(
      @(ContractAnnotation @field)("_broker", 0) _broker: String,
      @(ContractAnnotation @field)("_owner", 1) _owner: String,
      @(ContractAnnotation @field)("_cutoff", 2) _cutoff: BigInt)

  def apply(entry: Abi.Event): AllOrdersCancelledByBrokerEvent =
    new AllOrdersCancelledByBrokerEvent(entry)
}
