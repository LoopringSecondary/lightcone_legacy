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

import org.web3j.utils.Numeric

import scala.reflect.Manifest
import scala.reflect.runtime.universe._

object Deserialization {

  def getMethodParam(p: Any, r: Type) =
    if (r =:= typeOf[String]) {
      p match {
        case bs: Array[Byte] ⇒ Numeric.toHexString(bs)
        case _               ⇒ ""
      }
    } else if (r =:= typeOf[BigInt]) {
      p match {
        case bs: Array[Byte] ⇒ BigInt(bs)
        case b: BigInt       ⇒ b
        case b: BigInteger   ⇒ BigInt(b)
        case _               ⇒ BigInt(0)
      }
    }

  def deserialize[T](list: List[_])(implicit mf: Manifest[T]): T = {
    val rm = runtimeMirror(this.getClass.getClassLoader)
    val classSymbol: ClassSymbol = rm.classSymbol(mf.runtimeClass)
    val classMirror: ClassMirror = rm.reflectClass(classSymbol)
    val constructors = classSymbol.typeSignature.members.filter(_.isConstructor).toList
    val constructorMirror = classMirror.reflectConstructor(constructors.head.asMethod)

    val argsIdx = getContractAnnontationIdx[T]()
    assert(list.size == argsIdx.size)
    val params = constructorMirror.symbol.paramLists(0)
    val args1 = argsIdx map (i ⇒ getMethodParam(list(i), params(i).typeSignature))

    val v = constructorMirror(args1: _*)
    v.asInstanceOf[T]
  }

}
