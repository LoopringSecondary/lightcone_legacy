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

package org.loopring.lightcone.core

import org.loopring.lightcone.core.data._

package object depth {
  implicit class RichOrderbookSlot(this_ : XOrderbookSlot) {

    def +(that: XOrderbookSlot) = {
      assert(this_.slot == that.slot)
      XOrderbookSlot(
        this_.slot,
        this_.amount + that.amount,
        this_.total + that.total
      )
    }

    def -(that: XOrderbookSlot) = {
      assert(this_.slot == that.slot)
      XOrderbookSlot(
        this_.slot,
        this_.amount - that.amount,
        this_.total - that.total
      )
    }
  }
}
