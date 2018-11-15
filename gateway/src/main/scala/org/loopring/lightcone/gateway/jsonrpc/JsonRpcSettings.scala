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

package org.loopring.lightcone.gateway.jsonrpc

import com.google.inject.Injector
import com.google.inject.name.Names
import net.codingwell.scalaguice.InjectorExtensions._

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object JsonRpcSettings {

  def apply(): JsonRpcSettings = new JsonRpcSettings(Map.empty)
}

class JsonRpcSettings(ps: Map[String, MethodMirror]) {

  private[jsonrpc] lazy val m = runtimeMirror(getClass.getClassLoader)

  def register[T: ClassTag](instance: T): JsonRpcSettings = {

    println("====d=d=d=d=d=d===>>>" + instance)

    val iMr = m.reflect(instance)

    val providers = iMr.symbol.typeSignature.decls
      // .map(_.asMethod)
      .filter(filterMethod)
      .map {
        case symbol: MethodSymbol ⇒
          val mMr = iMr.reflectMethod(symbol)
          (symbol.name.encodedName.toString, mMr)
      }

    new JsonRpcSettings(providers.foldLeft(ps) { (_0, _tupl) ⇒
      _0 + (_tupl._1 → _tupl._2)
    })
  }

  def register[T: Manifest](
    implicit
    injector: Injector): JsonRpcSettings = {
    register(injector.instance[T])
  }

  def register[T: Manifest](named: String)(
    implicit
    injector: Injector): JsonRpcSettings = {
    register(injector.instance[T](Names.named(named)))
  }

  private[jsonrpc] def findProvider(method: String): Option[MethodMirror] = ps.get(method)

  private[jsonrpc] def filterMethod: PartialFunction[Symbol, Boolean] = {
    case m: Symbol ⇒ m.isMethod && m.isPublic && !m.isConstructor
  }

}

