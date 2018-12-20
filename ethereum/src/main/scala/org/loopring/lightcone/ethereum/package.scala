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

package object ethereum {
  implicit def int2BigInt(x: Int): BigInt = BigInt(x)

  implicit def string2BigInt(x: String): BigInt = x match {
    case n if n.length == 0 ⇒ BigInt(0)
    case p if p.startsWith("0x") ⇒ BigInt(p, 16)
    case _ ⇒ BigInt(x, 16)
  }

}
