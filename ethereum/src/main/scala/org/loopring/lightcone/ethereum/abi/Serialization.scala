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

import scala.reflect.Manifest
import scala.reflect.runtime.universe._

object Serialization {

  //  def serialize[T](t: T{def unapply(): Option[]})(implicit mf: Manifest[T]): Seq[Object] = {
  //    val argsIdx = getContractAnnontationIdx[T]()
  //    val rm = runtimeMirror(this.getClass.getClassLoader)
  //    val reflectT = rm.reflect(t)
  //    t.def unapply(arg: Serialization): Option[] =
  //    return Seq()
  //  }
}
