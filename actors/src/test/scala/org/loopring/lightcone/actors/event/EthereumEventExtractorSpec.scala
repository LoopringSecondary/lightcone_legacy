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

package org.loopring.lightcone.actors.event

import org.loopring.lightcone.actors.support._

class EthereumEventExtractorSpec
    extends CommonSpec
    with EthereumSupport
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with OrderGenerateSupport
    with OrderbookManagerSupport
    with EthereumEventExtractorSupport
  {

    "ethereum event extractor actor test" must {

      "correctly extract all kinds events from ethereum blocks" in {




      }

    }
}
