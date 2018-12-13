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

package org.loopring.lightcone.ethereum

import scala.reflect.runtime.universe._
import scala.reflect.Manifest

package object abi {

  private def getAnnotationValue[T](tree: Tree): T = tree match {
    case Literal(Constant(str: T)) => str
  }

  private[abi] def getContractAnnontationIdx[T](
    )(
      implicit mf: Manifest[T]
    ): Seq[Int] = {
    val typ = typeOf[T]
    (typ.members.filter { m =>
      m.annotations.nonEmpty && m.annotations.exists(
        _.tree.tpe =:= typeOf[ContractAnnotation]
      )
    } map { m =>
      val tree =
        m.annotations.find(_.tree.tpe =:= typeOf[ContractAnnotation]).get.tree
      val annArgs = tree.children.tail
      val name = getAnnotationValue[String](annArgs(0))
      val idx = getAnnotationValue[Int](annArgs(1))
      idx
    }).toSeq.reverse
  }

}
