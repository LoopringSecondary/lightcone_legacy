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

import com.typesafe.config.ConfigFactory
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.ethereum.event._
import org.loopring.lightcone.proto.AddressBalanceUpdated
import org.web3j.utils.Numeric

class EventExtractorSpec extends CommonSpec with EventExtractorSupport {

  override def beforeAll() {
    info(s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`")
  }

  "extract all kind of events " must {

    "correctly extract transfers, balance addresses and allowance addresses" in {

      val weth = Address("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2")
      val selfConfigStr = s"""
                             |weth {
                             |address = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"
                             |}
                             |loopring_protocol {
                             |  protocol-address = "0x8d8812b72d1e4ffCeC158D25f56748b7d67c1e78"
                             |  delegate-address = "0x17233e07c67d086464fD408148c3ABB56245FA64"
                             |  gas-limit-per-ring-v2 = "1000000",
                             |    burn-rate-table {
                             |    base = 1000,
                             |    tiers = [
                             |      {
                             |        name = "TIER_1"
                             |        tier = 3,
                             |        rate = 25
                             |      },
                             |      {
                             |        name = "TIER_2"
                             |        tier = 2,
                             |        rate = 150
                             |      },
                             |      {
                             |        name = "TIER_3"
                             |        tier = 1,
                             |        rate = 300
                             |      },
                             |      {
                             |        name = "TIER_4"
                             |        tier = 0,
                             |        rate = 500
                             |      },
                             |    ]
                             |  }
                             |}
     """.stripMargin

      val selfConfig = ConfigFactory.parseString(selfConfigStr)

      val transferExtractor = new TransferEventExtractor()(selfConfig)

      val transfers = (blockData.txs zip blockData.receipts).flatMap { item =>
        transferExtractor.extract(item._1, item._2, blockData.timestamp)
      }
      val (eths, tokens) = transfers.partition(tr => Address(tr.token).isZero)

      val cutOffExtractor = new CutoffEventExtractor()

      val cutOffs = (blockData.txs zip blockData.receipts).flatMap { item =>
        cutOffExtractor.extract(item._1, item._2, blockData.timestamp)
      }

      cutOffs.isEmpty should be(true)

      val onChainOrderExtractor = new OnchainOrderExtractor()

      val onChainOrders = (blockData.txs zip blockData.receipts).flatMap {
        item =>
          onChainOrderExtractor.extract(item._1, item._2, blockData.timestamp)
      }
      onChainOrders.isEmpty should be(true)

      val orderCanceledExtractor = new OrdersCancelledEventExtractor()

      val orderCancelledEvents =
        (blockData.txs zip blockData.receipts).flatMap { item =>
          orderCanceledExtractor.extract(item._1, item._2, blockData.timestamp)
        }
      orderCancelledEvents.isEmpty should be(true)

      val ringMinedEventExtractor = new RingMinedEventExtractor()

      val rings = (blockData.txs zip blockData.receipts).flatMap { item =>
        ringMinedEventExtractor.extract(item._1, item._2, blockData.timestamp)
      }
      rings.isEmpty should be(true)

      val tokenBurnRateExtractor = new TokenBurnRateEventExtractor()(selfConfig)

      val tokenBurnRates = (blockData.txs zip blockData.receipts).flatMap {
        item =>
          tokenBurnRateExtractor.extract(item._1, item._2, blockData.timestamp)
      }
      tokenBurnRates.isEmpty should be(true)

      val balanceExtractor = new BalanceChangedAddressExtractor

      val balances = (blockData.txs zip blockData.receipts).flatMap { item =>
        balanceExtractor.extract(item._1, item._2, blockData.timestamp)
      }.distinct

      val transferBalances = (transfers
        .filter(_.header.get.txStatus.isTxStatusSuccess)
        .flatMap(transfer => {
          Seq(
            AddressBalanceUpdated(transfer.from, transfer.token),
            AddressBalanceUpdated(transfer.to, transfer.token)
          )
        }) ++ blockData.txs.map(
        tx => AddressBalanceUpdated(tx.from, Address.ZERO.toString())
      )).distinct.filterNot(ba => Address(ba.address).equals(weth))

      (balances.size == transferBalances.size) should be(true)

      val allowanceExtractor =
        new AllowanceChangedAddressExtractor()(selfConfig)

      val allowances = (blockData.txs zip blockData.receipts).flatMap { item =>
        allowanceExtractor.extract(item._1, item._2, blockData.timestamp)
      }.distinct

      allowances.size should be(2)
    }
  }
}
