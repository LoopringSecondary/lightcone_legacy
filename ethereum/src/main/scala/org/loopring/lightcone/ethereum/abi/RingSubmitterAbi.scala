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

package org.loopring.lightcone.ethereum.abi

import scala.io.Source
import org.ethereum.solidity.{Abi => SABI}
import org.web3j.utils.Numeric

import scala.annotation.meta.field

class RingSubmitterAbi(abiJson: String) extends AbiWrap(abiJson) {

  val submitRing = SubmitRingsFunction(
    abi.findFunction(searchByName(SubmitRingsFunction.name))
  )

  val fEE_PERCENTAGE_BASE = FEE_PERCENTAGE_BASEFunction(
    abi.findFunction(searchByName(FEE_PERCENTAGE_BASEFunction.name))
  )

  val ringMinedEvent = RingMinedEvent(
    abi.findEvent(searchByName(RingMinedEvent.name))
  )

  val invalidRingEvent = InvalidRingEvent(
    abi.findEvent(searchByName(InvalidRingEvent.name))
  )

  override def unpackEvent(
      data: String,
      topics: Array[String]
    ): Option[Any] = {

    try {
      val event: SABI.Event = abi.findEvent(
        searchBySignature(
          Numeric.hexStringToByteArray(topics.headOption.getOrElse(""))
        )
      )
      event match {
        case _: SABI.Event =>
          event.name match {
            case RingMinedEvent.name =>
              ringMinedEvent.unpack(data, topics)
            case InvalidRingEvent.name =>
              invalidRingEvent.unpack(data, topics)
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
        case _: SABI.Function =>
          func.name match {
            case SubmitRingsFunction.name =>
              submitRing.unpackInput(data)
            case _ => None
          }
        case _ => None
      }
    } catch {
      case _: Throwable => None
    }

  }

}

object RingSubmitterAbi {

  val jsonStr: String =
    "[{\"constant\":false,\"inputs\":[{\"name\":\"data\",\"type\":\"bytes\"}],\"name\":\"submitRings\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"FEE_PERCENTAGE_BASE\",\"outputs\":[{\"name\":\"\",\"type\":\"uint16\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":false,\"name\":\"_ringIndex\",\"type\":\"uint256\"},{\"indexed\":true,\"name\":\"_ringHash\",\"type\":\"bytes32\"},{\"indexed\":true,\"name\":\"_feeRecipient\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"_fills\",\"type\":\"bytes\"}],\"name\":\"RingMined\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":false,\"name\":\"_ringHash\",\"type\":\"bytes32\"}],\"name\":\"InvalidRing\",\"type\":\"event\"}]"

  def apply(abiJson: String): RingSubmitterAbi = new RingSubmitterAbi(abiJson)

  def apply(): RingSubmitterAbi = new RingSubmitterAbi(jsonStr)
}

class SubmitRingsFunction(val entry: SABI.Function)
    extends AbiFunction[SubmitRingsFunction.Params, SubmitRingsFunction.Result]

object SubmitRingsFunction {
  val name = "submitRings"
  case class Params(@(ContractAnnotation @field)("data", 0) data: Array[Byte])

  case class Result()

  def apply(entry: SABI.Function): SubmitRingsFunction =
    new SubmitRingsFunction(entry)
}

class FEE_PERCENTAGE_BASEFunction(val entry: SABI.Function)
    extends AbiFunction[
      FEE_PERCENTAGE_BASEFunction.Params,
      FEE_PERCENTAGE_BASEFunction.Result
    ]

object FEE_PERCENTAGE_BASEFunction {
  val name = "FEE_PERCENTAGE_BASE"

  case class Params()

  case class Result(@(ContractAnnotation @field)("base", 0) base: BigInt)

  def apply(entry: SABI.Function): FEE_PERCENTAGE_BASEFunction =
    new FEE_PERCENTAGE_BASEFunction(entry)

}

class RingMinedEvent(val entry: SABI.Event)
    extends AbiEvent[RingMinedEvent.Result]

object RingMinedEvent {

  val name = "RingMined"

  case class Result(
      @(ContractAnnotation @field)("_ringIndex", 0) _ringIndex: BigInt,
      @(ContractAnnotation @field)("_ringHash", 1) _ringHash: String,
      @(ContractAnnotation @field)("_feeRecipient", 2) _feeRecipient: String,
      @(ContractAnnotation @field)("_fills", 3) _fills: String)

  def apply(entry: SABI.Event): RingMinedEvent = new RingMinedEvent(entry)

}

class InvalidRingEvent(val entry: SABI.Event)
    extends AbiEvent[InvalidRingEvent.Result]

object InvalidRingEvent {

  val name = "InvalidRing"

  case class Result(
      @(ContractAnnotation @field)("_ringHash", 0) _ringHash: String)

  def apply(entry: SABI.Event): InvalidRingEvent = new InvalidRingEvent(entry)

}
