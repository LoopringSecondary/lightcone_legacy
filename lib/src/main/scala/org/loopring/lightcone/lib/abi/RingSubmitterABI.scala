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
  val EN_INVALID_RING = "InvalidRing"

  val submitRing = findFunctionByName(FN_SUBMIT_RING)

  def decodeAndAssemble(tx: Transaction): Option[Any] = None

  def decodeAndAssemble(tx: Transaction, log: TransactionLog): Option[Any] = {
    val result = decode(log)
    val data = result.name match {
      case EN_RING_MINED   ⇒ assembleRingMinedEvent(result.list)
      case EN_INVALID_RING ⇒ assembleInvalidRingEvent(result.list)
      case _               ⇒ None
    }
    Option(data)
  }

  private[lib] def assembleRingMinedEvent(list: Seq[Any]) = {
    assert(list.length == 4, "length of RingMined event invalid")

    val fills = list(3) match {
      case l: Array[Object] ⇒ l.map(_ match {
        case f: Array[Object] ⇒
          assert(f.length == 8, "length of fill invalid")
          Fill(
            orderhash = scalaAny2Hex(f(0)),
            owner = scalaAny2Hex(f(1)),
            tokenS = scalaAny2Hex(f(2)),
            amountS = scalaAny2Bigint(f(3)),
            split = scalaAny2Bigint(f(4)),
            feeAmount = scalaAny2Bigint(f(5)),
            feeAmountS = scalaAny2Bigint(f(6)),
            feeAmountB = scalaAny2Bigint(f(7))
          )
        case _ ⇒ throw new Exception("")
      })

      case _ ⇒ throw new Exception("extract fills error")
    }

    RingMined(
      ringIndex = scalaAny2Bigint(list(0)),
      ringhash = scalaAny2Hex(list(1)),
      feeRecipient = scalaAny2Hex(list(2)),
      fills = fills
    )
  }

  private[lib] def assembleInvalidRingEvent(list: Seq[Any]) = {
    assert(list.length == 1, "length of InvalidRing event invalid")
    InvalidRing(scalaAny2Hex(list(0)))
  }

}
