package io.lightcone.relayer.integration.ordermonitor

import io.lightcone.core.OrderStatus._
import io.lightcone.core._
import io.lightcone.relayer.data._
import io.lightcone.relayer.getUniqueAccount
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration._
import org.scalatest._


class OrderMonitorSpec_DelayActive   extends FeatureSpec
  with GivenWhenThen
  with CommonHelper
  with ValidateHelper
  with Matchers {

  feature("order monitor ") {
    scenario("order delay active") {
      Given("an account with enough balance and allowance")
      implicit val account = getUniqueAccount()

      val getAccountReq = GetAccount.Req(
        address = account.getAddress,
        allTokens = true
      )
      getAccountReq.expectUntil(
        check((res: GetAccount.Res) => res.accountBalance.nonEmpty)
      )

      Then("submit an order that validUntil = now + 5 seconds")

      val order1 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "100".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals()),
        validUntil = (timeProvider.getTimeMillis / 1000).toInt + 5
      )

      SubmitOrder
        .Req(Some(order1))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Then("submit an order that validUntil = now + 60*60*24")

      val order2 = createRawOrder(
        tokenS = dynamicBaseToken.getAddress(),
        tokenB = dynamicQuoteToken.getAddress(),
        tokenFee = dynamicBaseToken.getAddress(),
        amountS = "100".zeros(dynamicBaseToken.getDecimals()),
        amountFee = "10".zeros(dynamicBaseToken.getDecimals()),
        validUntil = (timeProvider.getTimeMillis / 1000).toInt + 60 * 60 * 24
      )

      SubmitOrder
        .Req(Some(order2))
        .expect(check((res: SubmitOrder.Res) => res.success))

      Then("two orders just submitted are STATUS_PENDING")

      GetOrders
        .Req(owner = account.getAddress)
        .expect(
          containsInGetOrders(STATUS_PENDING, order1.hash, order2.hash)
        )
      Then("wait 10 seconds for order expire")

      Thread.sleep(10000)

      Then(
        s"${order1.hash} is STATUS_EXPIRED and ${order2.hash} is STATUS_PENDING"
      )

      GetOrders
        .Req(owner = account.getAddress)
        .expect(
          containsInGetOrders(STATUS_PENDING, order2.hash) and
            containsInGetOrders(STATUS_EXPIRED, order1.hash)
        )
    }
  }

}
