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

package org.loopring.lightcone.lib.data

import org.loopring.lightcone.lib.data._

// QUESTION(fukun):
// 这几个方法没有体现出签名，是不是应该改名为：
// getSignedTxInputData,
// getSignedTxData,
// 这几个方法说明意思和目的从签名上看不出来。
trait RingSigner {
  def generateInputData(ring: Ring): String
  def generateTxData(inputData: String, nonce: BigInt): Array[Byte]
  def getSignerAddress(): String
}
