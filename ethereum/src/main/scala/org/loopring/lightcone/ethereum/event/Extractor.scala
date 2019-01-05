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

package org.loopring.lightcone.ethereum.event

import org.loopring.lightcone.proto.TxStatus
import org.web3j.utils.Numeric

trait Extractor {

  def getStatus(status: String): TxStatus = {
    if (isSucceed(status)) {
      TxStatus.TX_STATUS_SUCCESS
    } else {
      TxStatus.TX_STATUS_FAILED
    }
  }

  def isSucceed(status: String): Boolean = {
    Numeric.toBigInt(status).intValue() == 1
  }

}
