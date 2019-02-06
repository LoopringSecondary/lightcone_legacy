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

class RingSubmitterAbi(abiJson: String) extends AbiWrap(abiJson) {

  val submitRing = SubmitRingsFunction(
    abi.findFunction(searchByName(SubmitRingsFunction.name))
  )

  val feePercentageBase = feePercentageBaseFunction(
    abi.findFunction(searchByName(feePercentageBaseFunction.name))
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
      val event: Abi.Event = abi.findEvent(
        searchBySignature(
          Numeric.hexStringToByteArray(topics.headOption.getOrElse(""))
        )
      )
      event match {
        case _: Abi.Event =>
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
        case _: Abi.Function =>
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
    Source.fromResource("version20/IRingSubmitter.abi").mkString
  def apply(abiJson: String): RingSubmitterAbi = new RingSubmitterAbi(abiJson)

  def apply(): RingSubmitterAbi = new RingSubmitterAbi(jsonStr)
}

class SubmitRingsFunction(val entry: Abi.Function)
    extends AbiFunction[SubmitRingsFunction.Params, SubmitRingsFunction.Result]

object SubmitRingsFunction {
  val name = "submitRings"
  case class Params(@(ContractAnnotation @field)("data", 0) data: Array[Byte])

  case class Result()

  def apply(entry: Abi.Function): SubmitRingsFunction =
    new SubmitRingsFunction(entry)
}

class feePercentageBaseFunction(val entry: Abi.Function)
    extends AbiFunction[
      feePercentageBaseFunction.Params,
      feePercentageBaseFunction.Result
    ]

object feePercentageBaseFunction {
  val name = "FEE_PERCENTAGE_BASE"

  case class Params()

  case class Result(@(ContractAnnotation @field)("base", 0) base: BigInt)

  def apply(entry: Abi.Function): feePercentageBaseFunction =
    new feePercentageBaseFunction(entry)

}

class RingMinedEvent(val entry: Abi.Event)
    extends AbiEvent[RingMinedEvent.Result]

object RingMinedEvent {

  val name = "RingMined"

  case class Result(
      @(ContractAnnotation @field)("_ringIndex", 0) _ringIndex: BigInt,
      @(ContractAnnotation @field)("_ringHash", 1) _ringHash: String,
      @(ContractAnnotation @field)("_feeRecipient", 2) _feeRecipient: String,
      @(ContractAnnotation @field)("_fills", 3) _fills: String)

  def apply(entry: Abi.Event): RingMinedEvent = new RingMinedEvent(entry)

}

class InvalidRingEvent(val entry: Abi.Event)
    extends AbiEvent[InvalidRingEvent.Result]

object InvalidRingEvent {

  val name = "InvalidRing"

  case class Result(
      @(ContractAnnotation @field)("_ringHash", 0) _ringHash: String)

  def apply(entry: Abi.Event): InvalidRingEvent = new InvalidRingEvent(entry)

}
