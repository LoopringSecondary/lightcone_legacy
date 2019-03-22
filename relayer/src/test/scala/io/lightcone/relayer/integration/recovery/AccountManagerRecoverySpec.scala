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

package io.lightcone.relayer.integration.recovery

import io.lightcone.relayer.actors.MultiAccountManagerActor
import io.lightcone.relayer.data.GetAccount
import io.lightcone.relayer.integration._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers._
import org.scalatest._

class AccountManagerRecoverySpec
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with ValidateHelper
    with Matchers {

  feature("test recovery") {
    scenario("account manager recovery") {
      implicit val account = getUniqueAccount()
      val entityNumOfAccountManager =
        config.getInt(s"${MultiAccountManagerActor.name}.num-of-entities")
      info(
        (account.getAddress.hashCode % entityNumOfAccountManager).abs.toString
      )

      GetAccount
        .Req(
          address = account.getAddress,
          allTokens = true
        )
        .expectUntil(
          check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
        )

    }
  }
}
