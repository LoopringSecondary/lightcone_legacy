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

class RingSubmitterABI(jsonStr: String) extends AbiWrap(jsonStr) {

  val FN_SUBMIT_RING = "submitRings"
  val EN_RING_MINED = "RingMined"

  val submitRing = findFunctionByName(FN_SUBMIT_RING)

  def decodeAndAssemble(tx: Transaction): Option[Any] = {
    val result = decode(tx.input)
    result.name match {
      case _ ⇒ None
    }
  }

  def decodeAndAssemble(tx: Transaction, log: TransactionLog): Option[Any] = {
    val result = decode(log)
    result.name match {
      case _ ⇒ None
    }
  }

}
