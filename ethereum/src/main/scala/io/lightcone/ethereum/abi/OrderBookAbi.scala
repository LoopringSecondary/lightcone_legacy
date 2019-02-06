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

import org.ethereum.solidity.Abi
import org.web3j.utils.Numeric

import scala.annotation.meta.field
import scala.io.Source

class OrderBookAbi(abiJson: String) extends AbiWrap(abiJson) {

  val orderSubmitted = OrderSubmittedFunction(
    abi.findFunction(searchByName(OrderSubmittedFunction.name))
  )

  val submitOrder = SubmitOrderFunction(
    abi.findFunction(searchByName(SubmitOrderFunction.name))
  )

  val orderSubmittedEvent = OrderSubmittedEvent(
    abi.findEvent(searchByName(OrderSubmittedEvent.name))
  )

  override def unpackEvent(
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
        case _: Abi.Event if event.name.equals(OrderSubmittedEvent.name) =>
          orderSubmittedEvent.unpack(data, topics)
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
            case OrderSubmittedFunction.name =>
              orderSubmitted.unpackInput(data)
            case SubmitOrderFunction.name =>
              submitOrder.unpackInput(data)
            case _ => None
          }
        case _ => None
      }
    } catch {
      case _: Throwable => None
    }
  }

}

object OrderBookAbi {

  val abiJsonStr: String =
    Source.fromResource("version2.0/IOrderBook.abi").mkString
  def apply(abiJson: String): OrderBookAbi = new OrderBookAbi(abiJson)

  def apply(): OrderBookAbi = new OrderBookAbi(abiJsonStr)

}

object OrderSubmittedFunction {
  case class Parms(@(ContractAnnotation @field)("hash", 0) hash: Array[Byte])

  case class Result(
      @(ContractAnnotation @field)("submitted", 0) submitted: Boolean)

  val name = "orderSubmitted"

  def apply(entry: Abi.Function): OrderSubmittedFunction =
    new OrderSubmittedFunction(entry)

}

class OrderSubmittedFunction(val entry: Abi.Function)
    extends AbiFunction[
      OrderSubmittedFunction.Parms,
      OrderSubmittedFunction.Result
    ]

object SubmitOrderFunction {
  case class Parms(
      @(ContractAnnotation @field)("orderData", 0) orderData: Array[Byte])

  case class Result()

  val name = "submitOrder"

  def apply(entry: Abi.Function): SubmitOrderFunction =
    new SubmitOrderFunction(entry)
}

class SubmitOrderFunction(val entry: Abi.Function)
    extends AbiFunction[SubmitOrderFunction.Parms, SubmitOrderFunction.Result]

object OrderSubmittedEvent {

  case class Result(
      @(ContractAnnotation @field)("orderHash", 0) orderHash: String,
      @(ContractAnnotation @field)("orderData", 1) orderData: String)

  val name = "OrderSubmitted"

  def apply(entry: Abi.Event): OrderSubmittedEvent =
    new OrderSubmittedEvent(entry)

}

class OrderSubmittedEvent(val entry: Abi.Event)
    extends AbiEvent[OrderSubmittedEvent.Result]
