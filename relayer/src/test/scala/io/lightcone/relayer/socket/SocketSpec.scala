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

package io.lightcone.relayer.socket

import io.lightcone.relayer.support._

class SocketSpec
    extends CommonSpec
    with EthereumSupport
    with MultiAccountManagerSupport
    with SocketSupport {

  "socket server  test" must {
    "socket server starts normally and can subscriber and received correct data" in {
//      Thread.sleep(Int.MaxValue)
    }
  }
}
