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

package io.lightcone.lib

import scala.util.hashing.MurmurHash3

object MurmurHash64 {
  private val seed = 0xf7ca7fd2

  def hash(u: String): Long = {
    val a = MurmurHash3.stringHash(u, seed)
    val b = MurmurHash3.stringHash(u.reverse, seed)
    (a.toLong << 32) | (b & 0XFFFFFFFFL)
  }
}
