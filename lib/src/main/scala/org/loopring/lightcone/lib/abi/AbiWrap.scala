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
import java.math.BigInteger
import java.lang.{ Boolean ⇒ jbool }

import org.apache.commons.collections4.Predicate
import org.web3j.utils.{ Numeric, Strings }
import org.loopring.lightcone.lib.solidity.{ SolidityAbi ⇒ SABI }

trait AbiFunction[P, R] {
  val entry: SABI.Function

  //todo：与原函数区分，使用pack与unpack
  def pack(t: P): Array[Byte]
  def unpackInput(data: Array[Byte]): Option[P]
  def unpackResult(data: Array[Byte]): Option[R]
}

trait AbiEvent[R] {
  val entry: SABI.Event

  def unpack(log: TransactionLog): Option[R]
}

//todo:最好是再彻底重写Abi,不再使用SolidityAbi
abstract class AbiWrap(abiJson: String) {

  protected var abi = SABI.fromJson(abiJson)
  //初始化所有的functions和events
  init()

  protected val functionSignatureLength = 8

  def getTransactionHeader(txInput: String): BigInt = Numeric.decodeQuantity(txInput)

  private[lib] var functions = Seq[AbiFunction]()
  private[lib] var events = Seq[AbiEvent]()

  def init():Unit
//  abi.toArray.foreach {
//    _ match {
//      case x: SABI.Function ⇒
//        val sig = x.encodeSignature()
//        val key = Numeric.toHexStringWithPrefixZeroPadded(
//          sig.bigInteger,
//          functionSignatureLength
//        ).toLowerCase
//
//        supportedFunctions += key → x
//
//      case x: SABI.Event ⇒
//        val sig = x.encodeSignature()
//        val key = Numeric.toHexString(sig).toLowerCase
//
//        supportedEventLogs += key -> x
//
//      case _ ⇒
//    }
//  }

//  def getFunction(input: String): Option[SABI.Function] = {
//    val sig = input.slice(0, functionSignatureLength + 2)
//    val key = sig.toLowerCase
//    supportedFunctions.get(key)
//  }
//
//  def getEvent(firstTopic: String): Option[SABI.Event] = {
//    val key = firstTopic.toLowerCase
//    supportedEventLogs.get(key)
//  }

  private[lib] def searchByName[T <: SABI.Entry](name: String): Predicate[T] = x ⇒ x.name.equals(name)
  //todo: test 字节数组的相等
  private[lib] def searchBySignature[T <: SABI.Entry](signature: Array[Byte]): Predicate[T] = x ⇒ x.encodeSignature().equals(signature)

  def findFunctionByName(name: String) = functions.find(_.entry.name == name)

  def findEventByName(name: String) = {
    events.find(_.entry.name == name)
  }

  def findFunctionBySignature(signature: Array[Byte]): Option[AbiFunction] = {
    functions.find(_.entry.encodeSignature() sameElements signature)
  }
  def findEventBySignature(signature: Array[Byte]): Option[AbiEvent]  = {
    events.find(_.entry.encodeSignature() sameElements signature)
  }

  case class DecodeResult(name: String, list: Seq[Any])

  def decode(input: String): DecodeResult = {
    getFunction(input) match {
      case Some(function) ⇒
        val cleanInput = Numeric.cleanHexPrefix(input).substring(functionSignatureLength)
        val str = Strings.zeros(functionSignatureLength) + cleanInput
        val bytes = Numeric.hexStringToByteArray(str)
        val seq = function.decode(bytes).toArray().toSeq
        DecodeResult(function.name, seq)

      case _ ⇒ DecodeResult("", Seq.empty)
    }
  }

  def decode(log: TransactionLog): DecodeResult = {
    getEvent(log.topics.head) match {
      case Some(event) ⇒
        val decodeddata = Numeric.hexStringToByteArray(log.data)
        val decodedtopics = log.topics.map(x ⇒ Numeric.hexStringToByteArray(x)).toArray
        val seq = event.decode(decodeddata, decodedtopics).toArray().toSeq
        DecodeResult(event.name, seq)

      case _ ⇒ DecodeResult("", Seq.empty)
    }
  }

  def decodeAndAssemble(tx: Transaction): Option[Any]
  def decodeAndAssemble(tx: Transaction, log: TransactionLog): Option[Any]

  def scalaAny2Hex(src: Any): String = src match {
    case bs: Array[Byte] ⇒ Numeric.toHexString(bs)
    case _               ⇒ throw new Exception("scala any convert to scala array byte error")
  }

  def scalaAny2Bigint(src: Any): BigInt = src match {
    case b: BigInteger ⇒ b
    case _             ⇒ throw new Exception("scala any convert to scala bigint error")
  }

}
