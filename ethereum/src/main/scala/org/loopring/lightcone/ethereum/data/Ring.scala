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

package org.loopring.lightcone.ethereum.data

import org.web3j.utils.Numeric
import org.web3j.crypto.{ Hash ⇒ web3Hash }

case class Ring(
    feeReceipt: String,
    miner: String,
    sig: String,
    ringOrderIndex: Seq[Seq[Int]], // todo change to map
    orders: Seq[Order],
    transactionOrigin: String
) {

  def hash = {
    val data = orders.foldLeft(Array[Byte]()) {
      (res, order) ⇒
        res ++
          Numeric.hexStringToByteArray(order.hash) ++
          Numeric.toBytesPadded(BigInt(order.waiveFeePercentage).bigInteger, 2)
    }
    Numeric.toHexString(web3Hash.sha3(data))
  }

  def getInputData()(implicit serializer: RingSerializer) = {
    serializer.serialize(this)
  }

}
