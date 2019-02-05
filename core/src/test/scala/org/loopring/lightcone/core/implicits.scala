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

import org.loopring.lightcone.lib.ErrorException
/// import org.loopring.lightcone.proto._

package object implicits {
  implicit class RichDouble(v: Double) {

    def toWei(tokenAddr: String)(implicit tm: MetadataManager) = {
      tm.getToken(tokenAddr)
        .getOrElse(
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            s"token no found for address $tokenAddr"
          )
        )
        .toWei(v)
    }

    def ! = BigInt(v.toLong)
  }

}
