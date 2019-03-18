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

package io.lightcone.relayer.integration

import io.lightcone.core.ErrorCode.ERR_NONE
import io.lightcone.core.OrderStatus.STATUS_SOFT_CANCELLED_BY_USER
import io.lightcone.relayer._
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.Metadatas._
import org.scalatest._

class CancelOrderSpec
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("cancel orders of status=STATUS_PENDING") {
    scenario("cancel by order_hash") {

      Given("an account with enough Balance")
      implicit val account = getUniqueAccount()
      implicit val marketPair = LRC_WETH_MARKET.getMarketPair
      val getAccountReq = GetAccount.Req(
        address = account.getAddress,
        allTokens = true
      )
      val accountInitRes = getAccountReq.expectUntil(
        check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
      )
      info(
        s"balance of this account:${account.getAddress} is :${accountInitRes.accountBalance}"
      )

      Then("submit an order.")
      val order = createRawOrder()
      val submitRes = SubmitOrder
        .Req(Some(order))
        .expect(check((res: SubmitOrder.Res) => true))
      info(s"the result of submit order is ${submitRes.success}")

      Then("cancel this order by hash.")
      val cancelReq =
        CancelOrder.Req(
          owner = order.owner,
          id = order.hash,
          status = STATUS_SOFT_CANCELLED_BY_USER,
          time = BigInt(timeProvider.getTimeSeconds())
        )
      val sig = generateCancelOrderSig(cancelReq)
      val cancelRes = cancelReq
        .withSig(sig)
        .expect(check { res: CancelOrder.Res =>
          res.error == ERR_NONE
        })

      Then("check the result.")
      defaultValidate(
        containsInGetOrders(order.hash),
        orderBookNonEmpty(),
        userFillsIsEmpty(),
        marketFillsIsEmpty(),
        be(accountInitRes)
      )

//      Res(Some(AccountBalance(0xf28116de890d78262f376dee35c7e9f00454c2d3,Map(0x0000000000000000000000000000000000000000 -> TokenBalance(0x0000000000000000000000000000000000000000,Some(Amount(<ByteString@6e584b05 size=9>,0)),Some(Amount(<ByteString@950c212 size=9>,0)),Some(Amount(<ByteString@1aedfbd size=9>,0)),Some(Amount(<ByteString@368a28d0 size=9>,0)),0), 0x2d7233f72af7a600a8ebdfa85558c047c1c8f795 -> TokenBalance(0x2d7233f72af7a600a8ebdfa85558c047c1c8f795,Some(Amount(<ByteString@1a265d67 size=9>,0)),Some(Amount(<ByteString@15e94c63 size=9>,0)),Some(Amount(<ByteString@62cd3a54 size=9>,0)),Some(Amount(<ByteString@5e64275 size=9>,0)),0), 0x97241525fe425c90ebe5a41127816dcfa5954b06 -> TokenBalance(0x97241525fe425c90ebe5a41127816dcfa5954b06,Some(Amount(<ByteString@2d03eacb size=9>,0)),Some(Amount(<ByteString@7256315c size=9>,0)),Some(Amount(<ByteString@79c2aad8 size=9>,0)),Some(Amount(<ByteString@4e00ba7f size=9>,0)),0), 0x7cb592d18d0c49751ba5fce76c1aec5bdd8941fc -> TokenBalance(0x7cb592d18d0c49751ba5fce76c1aec5bdd8941fc,Some(Amount(<ByteString@7c6754c6 size=9>,0)),Some(Amount(<ByteString@3ff856c7 size=9>,0)),Some(Amount(<ByteString@5300af8a size=9>,0)),Some(Amount(<ByteString@189834d7 size=9>,0)),0))))) was not equal to Res(Some(AccountBalance(0xf28116de890d78262f376dee35c7e9f00454c2d3,Map(0x7cb592d18d0c49751ba5fce76c1aec5bdd8941fc -> TokenBalance(0x7cb592d18d0c49751ba5fce76c1aec5bdd8941fc,Some(Amount(<ByteString@46f42f9f size=9>,0)),Some(Amount(<ByteString@1567310d size=9>,0)),Some(Amount(<ByteString@6448deb5 size=9>,0)),Some(Amount(<ByteString@4781b924 size=9>,0)),0), 0x97241525fe425c90ebe5a41127816dcfa5954b06 -> TokenBalance(0x97241525fe425c90ebe5a41127816dcfa5954b06,Some(Amount(<ByteString@206efe9c size=9>,0)),Some(Amount(<ByteString@cad5cc5 size=9>,0)),Some(Amount(<ByteString@2eb28693 size=9>,0)),Some(Amount(<ByteString@643052f8 size=9>,0)),0), 0x2d7233f72af7a600a8ebdfa85558c047c1c8f795 -> TokenBalance(0x2d7233f72af7a600a8ebdfa85558c047c1c8f795,Some(Amount(<ByteString@337dcafc size=9>,0)),Some(Amount(<ByteString@55d0f322 size=9>,0)),Some(Amount(<ByteString@6fec7115 size=9>,0)),Some(Amount(<ByteString@92d4982 size=9>,0)),0)))))
    }
  }
}
