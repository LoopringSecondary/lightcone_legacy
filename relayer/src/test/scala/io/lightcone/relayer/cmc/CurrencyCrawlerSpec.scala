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

package io.lightcone.relayer.cmc

import io.lightcone.cmc.CurrencyData
import io.lightcone.relayer.external.{CurrencyManager, SinaCurrencyManagerImpl}
import io.lightcone.relayer.support._
import scala.concurrent.Await
import scala.concurrent.duration._

class CurrencyCrawlerSpec
    extends CommonSpec
    with HttpSupport
    with EthereumSupport
    with DatabaseModuleSupport
    with MetadataManagerSupport {

  "currency crawler" must {
    "get currency from sina" in {
      val manager: CurrencyManager = new SinaCurrencyManagerImpl()
      val r =
        Await.result(manager.getUsdCnyCurrency().mapTo[CurrencyData], 5.second)
      r.currency > 0 should be(true)
    }
  }

}
