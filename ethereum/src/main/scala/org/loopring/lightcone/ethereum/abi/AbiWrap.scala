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

import java.math.BigInteger

import org.apache.commons.collections4.Predicate
import org.ethereum.solidity.{ Abi ⇒ SABI }
import org.web3j.utils.{ Numeric, Strings }

import scala.annotation.StaticAnnotation
import scala.collection.JavaConverters._
import scala.reflect.Manifest

case class ContractAnnotation(name: String, idx: Int) extends StaticAnnotation

trait AbiFunction[P, R] {
  val entry: SABI.Function

  //与原函数区分，使用pack与unpack
  def pack(t: P): Array[Byte] = ???

  def unpackInput(data: Array[Byte])(implicit mf: Manifest[P]): Option[P] = {
    val list = entry.decode(data).asScala.toList
    if (list.isEmpty) None
    else Some(Deserialization.deserialize[P](list))
  }

  def unpackResult(data: Array[Byte])(implicit mf: Manifest[R]): Option[R] = {
    val list = entry.decode(data).asScala.toList
    if (list.isEmpty) None
    else Some(Deserialization.deserialize[R](list))
  }
}

trait AbiEvent[R] {
  val entry: SABI.Event

  def unpack(data: Array[Byte], topics: Array[Array[Byte]])(implicit mf: Manifest[R]): Option[R] = {
    val list = entry.decode(data, topics).asScala.toList
    if (list.isEmpty) None
    else Some(Deserialization.deserialize[R](list))
  }
}

//todo:最好是再彻底重写Abi,不再使用SolidityAbi
abstract class AbiWrap(abiJson: String) {

  protected var abi = SABI.fromJson(abiJson)

  def getTransactionHeader(txInput: String): BigInt = Numeric.decodeQuantity(txInput)

  private[abi] def searchByName[T <: SABI.Entry](name: String): Predicate[T] = x ⇒ x.name.equals(name)

  //todo: test 字节数组的相等
  private[abi] def searchBySignature[T <: SABI.Entry](signature: Array[Byte]): Predicate[T] = x ⇒ x.encodeSignature().equals(signature)

}
