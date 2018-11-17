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

package org.loopring.lightcone.lib

import java.math.BigInteger
import java.lang.{ Boolean ⇒ jbool }

import org.web3j.utils.{ Numeric, Strings }
import org.loopring.lightcone.lib.solidity.{ SolidityAbi ⇒ SABI }

import scala.collection.mutable.{ HashMap ⇒ MMap }

abstract class AbiWrap(abiJson: String) {

  val abi: SABI = SABI.fromJson(abiJson)
  val functionSignatureLength = 8

  def getTransactionHeader(txInput: String): BigInt = {
    Numeric.decodeQuantity(txInput)
  }

  val supportedFunctions: MMap[String, SABI.Function] = MMap.empty[String, SABI.Function]
  val supportedEventLogs: MMap[String, SABI.Event] = MMap.empty[String, SABI.Event]

  abi.toArray().foreach {
    _ match {
      case x: SABI.Function ⇒
        val sig = x.encodeSignature()
        val key = Numeric.toHexStringWithPrefixZeroPadded(sig.bigInteger, functionSignatureLength)
        supportedFunctions += key.toLowerCase → x
      case x: SABI.Event ⇒
        val sig = x.encodeSignature()
        val key = Numeric.toHexString(sig)
        supportedEventLogs += key.toLowerCase -> x
      case _ ⇒
    }
  }

  def getFunction(input: String): Option[SABI.Function] = {
    val sig = input.slice(0, functionSignatureLength + 2)
    val key = sig.toLowerCase
    supportedFunctions.get(key)
  }

  def getEvent(firstTopic: String): Option[SABI.Event] = {
    val key = firstTopic.toLowerCase
    supportedEventLogs.get(key)
  }

  case class decodeResult(name: String, list: Seq[Any])

  def decode(input: String): decodeResult = {
    getFunction(input) match {
      case Some(function) ⇒
        val cleanInput = Numeric.cleanHexPrefix(input).substring(functionSignatureLength)
        val str = Strings.zeros(functionSignatureLength) + cleanInput
        val bytes = Numeric.hexStringToByteArray(str)
        val seq = function.decode(bytes).toArray().toSeq
        decodeResult(function.name, seq)

      case _ ⇒ decodeResult("", Seq.empty)
    }
  }

  def decode(log: TransactionLog): decodeResult = {
    getEvent(log.topics.head) match {
      case Some(event) ⇒
        val decodeddata = Numeric.hexStringToByteArray(log.data)
        val decodedtopics = log.topics.map(x ⇒ Numeric.hexStringToByteArray(x)).toArray
        val seq = event.decode(decodeddata, decodedtopics).toArray().toSeq
        decodeResult(event.name, seq)

      case _ ⇒ decodeResult("", Seq.empty)
    }
  }

  def decodeAndAssemble(tx: Transaction): Option[Any]
  def decodeAndAssemble(tx: Transaction, log: TransactionLog): Option[Any]

  // 这里比较特殊 涉及到任意类型的强制转换 只有abi转换时用到 所以放到该接口
  def javaObj2Hex(src: Object): String = src match {
    case bs: Array[Byte] ⇒ Numeric.toHexString(bs)
    case _               ⇒ throw new Exception("java object convert to scala string error")
  }

  def javaObj2Bigint(src: Object): BigInt = src match {
    case bs: BigInteger ⇒ BigInt(bs)
    case _              ⇒ throw new Exception("java object convert to scala bigint error")
  }

  def javaObj2Boolean(src: Object): Boolean = src match {
    case b: jbool ⇒ b
    case _        ⇒ throw new Exception("java object convert to scala boolean error")
  }

  def scalaAny2Hex(src: Any): String = src match {
    case bs: Array[Byte] ⇒ Numeric.toHexString(bs)
    case _               ⇒ throw new Exception("scala any convert to scala array byte error")
  }

  def scalaAny2Bigint(src: Any): BigInt = src match {
    case b: BigInteger ⇒ b
    case _             ⇒ throw new Exception("scala any convert to scala bigint error")
  }

  def scalaAny2Bool(src: Any): Boolean = src match {
    case b: Boolean ⇒ b
    case _          ⇒ throw new Exception("scala any convert to scala bool error")
  }
}
