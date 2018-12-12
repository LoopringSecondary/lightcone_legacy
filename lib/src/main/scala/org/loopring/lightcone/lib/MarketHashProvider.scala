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

package org.loopring.lightcone.lib

object MarketHashProvider {

  private def removePrefix(address: String): String = {
    if (address.startsWith("0x")) {
      address.substring(2).trim
    } else {
      address.trim
    }
  }

  def convert2BigInt(address1: String, address2: String): BigInt = {
    BigInt(removePrefix(address1), 16) ^ BigInt(removePrefix(address2), 16)
  }

  def convert2Hex(address1: String, address2: String): String = {
    "0x" + convert2BigInt(address1, address2).toString(16)
  }
}
