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

package io.lightcone.relayer
import io.lightcone.relayer.support._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.ethereum.EthereumAccessActor
import akka.pattern._
import io.lightcone.core.Amount
import io.lightcone.lib.NumericConversion
import org.json4s
import org.json4s.JString
import scalapb.json4s.{JsonFormat, JsonFormatException, Printer}

import scala.concurrent.{Await, Future}

class GenerateTestDataSpec
    extends CommonSpec
    with HttpSupport
    with EthereumSupport
    with JsonrpcSupport {
  "generate test data" must {
    "correctly send all transactions and get transactions and receipts" in {

      val account0 = accounts.head
      val account1 = getUniqueAccountWithoutEth
      val account2 = getUniqueAccountWithoutEth
      val hash1 = Await.result(
        transferEth(account1.getAddress, "20")(account0)
          .mapAs[SendRawTransaction.Res]
          .map(_.result),
        timeout.duration
      )

      Await.result(
        transferEth(account2.getAddress, "10")(account0),
        timeout.duration
      )
      val hash2 = Await.result(
        transferWETH(account2.getAddress, "20")(account0)
          .mapAs[SendRawTransaction.Res]
          .map(_.result),
        timeout.duration
      )
      Thread.sleep(2000)
      val hash3: String = Await.result(
        wrap("10")(account1)
          .mapAs[SendRawTransaction.Res]
          .map(_.result),
        timeout.duration
      )
      val hash4: String = Await.result(
        unwrap("10")(account2)
          .mapAs[SendRawTransaction.Res]
          .map(_.result),
        timeout.duration
      )
      Thread.sleep(2000)

      val hash5: String =
        Await.result(
          approveWETHToDelegate("100")(account1)
            .mapAs[SendRawTransaction.Res]
            .map(_.result),
          timeout.duration
        )
      val hash6: String =
        Await.result(
          approveLRCToDelegate("0")(account2)
            .mapAs[SendRawTransaction.Res]
            .map(_.result),
          timeout.duration
        )

      Thread.sleep(2000)

      val hashes: Seq[String] = Seq(hash1, hash2, hash3, hash4, hash5, hash6)

      val receipts = Await.result(
        (actors.get(EthereumAccessActor.name) ? BatchGetTransactionReceipts.Req(
          hashes.map(
            hash => GetTransactionReceipt.Req(hash)
          )
        )).mapAs[BatchGetTransactionReceipts.Res]
          .map(_.resps.map(_.result.get)),
        timeout.duration
      )

      val transactions = Await.result(
        (actors.get(EthereumAccessActor.name) ? BatchGetTransactions.Req(
          hashes.map(
            hash => GetTransactionByHash.Req(hash)
          )
        )).mapAs[BatchGetTransactions.Res]
          .map(_.resps.map(_.getResult)),
        timeout.duration
      )

      val formatRegistry =
        JsonFormat.DefaultRegistry
          .registerWriter[Amount](
            (amount: Amount) =>
              JString(
                NumericConversion.toHexString(BigInt(amount.value.toByteArray))
              ), {
              case JString(str) => null // this should never happen
              case _            => throw new JsonFormatException("Expected a string.")
            }
          )

      val printer = new Printer(formatRegistry = formatRegistry)

      println(receipts.map(printer.print))

      println(transactions.map(printer.print))
    }

  }
}
