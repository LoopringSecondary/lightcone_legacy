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

package org.loopring.lightcone.ethereum.abi

import org.scalatest._
import org.web3j.utils.Numeric

class OrderCancellerAbiSpec
  extends FlatSpec
  with Matchers
  with BeforeAndAfterAll {

  val orderCancellerAbi = OrderCancellerAbi()

  override def beforeAll() {
    info(s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`")
  }

  "encodeCancelOrdersFunction" should "encode class Params to input" in {
    val orderHashes =
      "0x7aa6bab45d654cbf3c335730c58f62ab93449d1099968659b7a9f96fdda603f8"
    val params =
      CancelOrdersFunction.Params(Numeric.hexStringToByteArray(orderHashes))
    val input = orderCancellerAbi.cancelOrders.pack(params)
    info(input)
    input should be(
      "0xa383de3a000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000207aa6bab45d654cbf3c335730c58f62ab93449d1099968659b7a9f96fdda603f8")
  }

  "decodeCancelOrdersFunction" should "decode  function input  to  CancelOrdersFunction function params" in {
    val input =
      "0xa383de3a000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000207aa6bab45d654cbf3c335730c58f62ab93449d1099968659b7a9f96fdda603f8"
    val params = orderCancellerAbi.cancelOrders.unpackInput(input)

    params.map(
      param =>
        Numeric.toHexString(param.orderHashes) should be(
          "0x7aa6bab45d654cbf3c335730c58f62ab93449d1099968659b7a9f96fdda603f8"))
  }

  "decodeOrdersCancelledEvent" should "decode event data to OrdersCancelledEvent Result" in {
    val data =
      "0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000017aa6bab45d654cbf3c335730c58f62ab93449d1099968659b7a9f96fdda603f8"
    val topics = Seq(
      "0xa4f958623cd4c90b4a1213ca9f26b442398e4efad078048b6730c826fa4e5da1",
      "0x000000000000000000000000cc1cf2a03c023e12426b0047c3d07e30f4e1d103")
    val result =
      orderCancellerAbi.ordersCancelledEvent.unpack(data, topics.toArray)

    result.foreach(res => {
      res.address should be("0xcc1cf2a03c023e12426b0047c3d07e30f4e1d103")
      res._orderHashes.length should be(1)
      res._orderHashes.head should be(
        "0x7aa6bab45d654cbf3c335730c58f62ab93449d1099968659b7a9f96fdda603f8")
    })
  }

  "encodeCancelAllOrdersForMarketKeyFunction" should "encode CancelAllOrdersForMarketKeyFunction Params to input" in {
    val token1 = "0x5399b819f14e55683bd90cacb61f13203bef4d63"
    val token2 = "0xb6beb7c8a3394098c485172d5636c1e107fee6f6"
    val cutOff = BigInt(1544612294)
    val params =
      CancelAllOrdersForMarketKeyFunction.Params(token1, token2, cutOff)
    val input = orderCancellerAbi.cancelAllOrdersForMarketKey.pack(params)

    input should be(
      "0xeac5c1900000000000000000000000005399b819f14e55683bd90cacb61f13203bef4d63000000000000000000000000b6beb7c8a3394098c485172d5636c1e107fee6f6000000000000000000000000000000000000000000000000000000005c10e9c6")

  }

  "decodeCancelAllOrdersForMarketKeyFunction" should "decode input data to cancelAllOrdersForMarketKey Params" in {
    val input =
      "0xeac5c1900000000000000000000000005399b819f14e55683bd90cacb61f13203bef4d63000000000000000000000000b6beb7c8a3394098c485172d5636c1e107fee6f6000000000000000000000000000000000000000000000000000000005c10e9c6"

    val params =
      orderCancellerAbi.cancelAllOrdersForMarketKey.unpackInput(input)
    info(params.toString)
    params.foreach { param =>
      param.token1 should be("0x5399b819f14e55683bd90cacb61f13203bef4d63")
      param.token2 should be("0xb6beb7c8a3394098c485172d5636c1e107fee6f6")
      param.cutoff.toString() should be("1544612294")
    }
  }

  "decodeAllOrdersCancelledForMarketKeyEvent" should "decode event data to AllOrdersCancelledForMarketKeyEvent Result" in {
    val data =
      "0x0000000000000000000000005399b819f14e55683bd90cacb61f13203bef4d63000000000000000000000000b6beb7c8a3394098c485172d5636c1e107fee6f6000000000000000000000000000000000000000000000000000000005c10e9c6"
    val topics = Seq(
      "0x03010b5cc0bf0153a225153a75169278a98eb21343f06fbff672a758a85b64a6",
      "0x00000000000000000000000007d24603d5fb6cdff728a7be7a04a26b7fcc20d9")
    val result = orderCancellerAbi.allOrdersCancelledForMarketKeyEvent
      .unpack(data, topics.toArray)
    info(result.toString)
    result.foreach(res => {
      res._broker should be("0x07d24603d5fb6cdff728a7be7a04a26b7fcc20d9")
      res._token1 should be("0x5399b819f14e55683bd90cacb61f13203bef4d63")
      res._token2 should be("0xb6beb7c8a3394098c485172d5636c1e107fee6f6")
      res._cutoff.toString() should be("1544612294")
    })
  }

  "encodeCancelAllOrdersFunction" should "encode cancel all orders function params to input" in {
    val cutOff = BigInt(1544614733)
    val input = orderCancellerAbi.cancelAllOrders.pack(
      CancelAllOrdersFunction.Params(cutOff))
    input should be(
      "0xbd545f53000000000000000000000000000000000000000000000000000000005c10f34d")
  }

  "decodeCancelAllOrdersFunction" should "decode input to cancel all orders function params" in {
    val input =
      "0xbd545f53000000000000000000000000000000000000000000000000000000005c10f34d"
    var params = orderCancellerAbi.cancelAllOrders.unpackInput(input)
    info(params.toString)
    params.map { param =>
      param.cutoff.toString should be("1544614733")
    }
  }

  "decodeAllOrdersCanceledEvent" should "decode event data to AllOrdersCanceledEvent Result" in {
    val data =
      "0x000000000000000000000000000000000000000000000000000000005c10f34d"
    val topics = Seq(
      "0x83a782ac7424737a1190d4668474e765f07d603de0485a081dbc343ac1b02099",
      "0x000000000000000000000000cc1cf2a03c023e12426b0047c3d07e30f4e1d103")
    val result =
      orderCancellerAbi.allOrdersCancelledEvent.unpack(data, topics.toArray)
    info(result.toString)
    result.map { res =>
      res._broker should be("0xcc1cf2a03c023e12426b0047c3d07e30f4e1d103")
      res._cutoff.toString should be("1544614733")
    }
  }

  "encodeCancelAllOrdersForMarketKeyOfOwnerFunction" should "encode CancelAllOrdersForMarketKeyOfOwnerFunction params to input" in {
    val owner = "0x07d24603d5fb6cdff728a7be7a04a26b7fcc20d9"
    val token1 = "0xcbb0b3d6dc184aa31625d94fc03db7965cbfb7f7"
    val token2 = "0x1a81b84927c57e94e5dd99b02af4119d47035506"
    val cutOff = BigInt(1544615992)
    val params = CancelAllOrdersForMarketKeyOfOwnerFunction.Params(
      owner = owner,
      token1 = token1,
      token2 = token2,
      cutoff = cutOff)
    val input =
      orderCancellerAbi.cancelAllOrdersForMarketKeyOfOwner.pack(params)

    input should be(
      "0x22baa82600000000000000000000000007d24603d5fb6cdff728a7be7a04a26b7fcc20d9000000000000000000000000cbb0b3d6dc184aa31625d94fc03db7965cbfb7f70000000000000000000000001a81b84927c57e94e5dd99b02af4119d47035506000000000000000000000000000000000000000000000000000000005c10f838")
  }

  "decodeCancelAllOrdersForMarketKeyOfOwnerFunction" should "decode input to cancelAllOrdersForMarketKeyOfOwner Params" in {
    val input =
      "0x22baa82600000000000000000000000007d24603d5fb6cdff728a7be7a04a26b7fcc20d9000000000000000000000000cbb0b3d6dc184aa31625d94fc03db7965cbfb7f70000000000000000000000001a81b84927c57e94e5dd99b02af4119d47035506000000000000000000000000000000000000000000000000000000005c10f838"
    val params =
      orderCancellerAbi.cancelAllOrdersForMarketKeyOfOwner.unpackInput(input)
    info(params.toString)
    params.map { param =>
      OrdersCancelledEvent
      param.owner should be("0x07d24603d5fb6cdff728a7be7a04a26b7fcc20d9")
      param.token1 should be("0xcbb0b3d6dc184aa31625d94fc03db7965cbfb7f7")
      param.token2 should be("0x1a81b84927c57e94e5dd99b02af4119d47035506")
      param.cutoff.toString() should be("1544615992")

    }
  }

  "decodeCancelAllOrdersForMarketKeyOfOwnerEvent" should "decode event data to CancelAllOrdersForMarketKeyOfOwner result" in {
    val data =
      "0x000000000000000000000000cbb0b3d6dc184aa31625d94fc03db7965cbfb7f70000000000000000000000001a81b84927c57e94e5dd99b02af4119d47035506000000000000000000000000000000000000000000000000000000005c10f838"
    val topics = Seq(
      "0x97bda78b7834715d21505da9c8801f7528412b8b3a6d49e6acad56a0ca9c5086",
      "0x000000000000000000000000317863d48454d193f1df864b9b374cd397157dd4",
      "0x00000000000000000000000007d24603d5fb6cdff728a7be7a04a26b7fcc20d9")
    val result = orderCancellerAbi.allOrdersCancelledForMarketKeyByBrokerEvent
      .unpack(data, topics.toArray)
    result.map { res =>
      {
        res._broker should be("0x317863d48454d193f1df864b9b374cd397157dd4")
        res._owner should be("0x07d24603d5fb6cdff728a7be7a04a26b7fcc20d9")
        res._token1 should be("0xcbb0b3d6dc184aa31625d94fc03db7965cbfb7f7")
        res._token2 should be("0x1a81b84927c57e94e5dd99b02af4119d47035506")
        res._cutoff.toString() should be("1544615992")
      }
    }
  }

  "encodeCancelAllOrdersOfOwnerFunction" should "encode CancelAllOrdersOfOwnerFunction Params to input" in {
    val owner = "0xcc1cf2a03c023e12426b0047c3d07e30f4e1d103"
    val cutoff = BigInt(1544617791)
    val params = CancelAllOrdersOfOwnerFunction.Params(owner, cutoff)
    val input = orderCancellerAbi.cancelAllOrdersOfOwner.pack(params)
    input should be(
      "0x543b5fa4000000000000000000000000cc1cf2a03c023e12426b0047c3d07e30f4e1d103000000000000000000000000000000000000000000000000000000005c10ff3f")

  }

  "decodeCancelAllOrdersOfOwnerFunction" should "decode input to CancelAllOrdersOfOwnerFunction Params" in {
    val input =
      "0x543b5fa4000000000000000000000000cc1cf2a03c023e12426b0047c3d07e30f4e1d103000000000000000000000000000000000000000000000000000000005c10ff3f"
    val params = orderCancellerAbi.cancelAllOrdersOfOwner.unpackInput(input)
    info(params.toString)
    params.map { res =>
      res.owner should be("0xcc1cf2a03c023e12426b0047c3d07e30f4e1d103")
      res.cutoff.toString should be("1544617791")
    }
  }

  "decodeAllOrdersCancelledByBrokerEvent" should "decode event data AllOrdersCancelledByBrokerEvent result" in {
    val data =
      "0x000000000000000000000000000000000000000000000000000000005c10ff3f"
    val topics = Seq(
      "0x6c1800f274202aaab5d7cdb6ef958ce1c3a6ffe82696c4a872f11547485e36c4",
      "0x000000000000000000000000317863d48454d193f1df864b9b374cd397157dd4",
      "0x000000000000000000000000cc1cf2a03c023e12426b0047c3d07e30f4e1d103")
    val result = orderCancellerAbi.allOrdersCancelledByBrokerEvent
      .unpack(data, topics.toArray)
    result.map { res =>
      res._broker should be("0x317863d48454d193f1df864b9b374cd397157dd4")
      res._owner should be("0xcc1cf2a03c023e12426b0047c3d07e30f4e1d103")
      res._cutoff.toString should be("1544617791")
    }
  }
}
