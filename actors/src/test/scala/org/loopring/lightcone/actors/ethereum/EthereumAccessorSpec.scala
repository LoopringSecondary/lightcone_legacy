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

package org.loopring.lightcone.actors.ethereum

import akka.actor._
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config._
import org.loopring.lightcone.actors.base.MapBasedLookup
import org.loopring.lightcone.actors.support.{CommonSpec, EthereumSupport}
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.lib.SystemTimeProvider
import org.loopring.lightcone.proto._
import org.scalatest._
import org.slf4s.Logging
import org.web3j.utils.Numeric

import scala.concurrent.duration._
import scala.concurrent._

class EthereumAccessorSpec extends CommonSpec("""
                                                |akka.cluster.roles=[]
                                                |""".stripMargin) with EthereumSupport {

  val wethAbi = WETHABI()
  val delegateAdderess = "0x17233e07c67d086464fD408148c3ABB56245FA64"

  val ethereumAccessActor = actors.get(EthereumAccessActor.name)

  val fu = for {
    blockNum ← (ethereumAccessActor ? GetBlockNumber.Req())
      .mapTo[GetBlockNumber.Res]
      .map(_.result)
    //    blockWithTxHash ← (ethereumAccessActor ? GetBlockWithTxHashByNumber.Req(
    //      blockNum
    //    )).mapTo[GetBlockWithTxHashByNumber.Res]
    //      .map(_.result.get)
    //    blockWithTxObjcet ← (ethereumAccessActor ? GetBlockWithTxObjectByNumber.Req(
    //      blockNum
    //    )).mapTo[GetBlockWithTxObjectByNumber.Res]
    //      .map(_.result.get)
    //    blockByHashWithHash ← (ethereumAccessActor ? GetBlockWithTxHashByHash.Req(
    //      blockWithTxHash.hash
    //    )).mapTo[GetBlockWithTxHashByHash.Res]
    //      .map(_.result.get)
    //    blockByHashwithObject ← (ethereumAccessActor ? GetBlockWithTxObjectByHash.Req(
    //      blockWithTxHash.hash
    //    )).mapTo[GetBlockWithTxObjectByHash.Res]
    //      .map(_.result.get)
    //    txs ← Future.sequence(blockWithTxHash.transactions.take(1).map { hash ⇒
    //      (ethereumAccessActor ? GetTransactionByHash.Req(hash))
    //        .mapTo[GetTransactionByHash.Res]
    //        .map(_.result.get)
    //    })
    //    receipts ← Future
    //      .sequence(blockWithTxHash.transactions.take(1).map { hash ⇒
    //        (ethereumAccessActor ? GetTransactionReceipt.Req(hash))
    //          .mapTo[GetTransactionReceipt.Res]
    //          .map(_.result.get)
    //      })
    //    batchReceipts ← (ethereumAccessActor ? BatchGetTransactionReceipts.Req(
    //      blockWithTxHash.transactions.map(GetTransactionReceipt.Req(_))
    //    )).mapTo[BatchGetTransactionReceipts.Res]
    //      .map(_.resps.map(_.result.get))
    //    nonce ← (ethereumAccessActor ? GetNonce.Req(
    //      owner = "0xdce9e65ba38d4249c38d00d664d41e5f6d7e83b3",
    //      tag = "latest"
    //    )).mapTo[GetNonce.Res]
    //      .map(_.result)
    //    txCount ← (ethereumAccessActor ? GetBlockTransactionCount.Req(
    //      blockWithTxHash.hash
    //    )).mapTo[GetBlockTransactionCount.Res]
    //      .map(_.result)
    //    batchTx ← (ethereumAccessActor ? BatchGetTransactions.Req(
    //      blockWithTxHash.transactions.map(GetTransactionByHash.Req(_))
    //    )).mapTo[BatchGetTransactions.Res]
    //      .map(_.resps.map(_.result))
    //    lrcBalance ← (ethereumAccessActor ? EthCall.Req(tag = "latest")
    //      .withParam(
    //        TransactionParams()
    //          .withData(
    //            wethAbi.balanceOf.pack(
    //              BalanceOfFunction
    //                .Parms("0xb94065482ad64d4c2b9252358d746b39e820a582")
    //            )
    //          )
    //          .withTo("0xef68e7c694f40c8202821edf525de3782458639f")
    //      ))
    //      .mapTo[EthCall.Res]
    //      .map(resp ⇒ wethAbi.balanceOf.unpackResult(resp.result))
    //    lrcbalances ← (ethereumAccessActor ? BatchCallContracts.Req(
    //      batchTx.map(
    //        tx ⇒
    //          EthCall.Req(tag = "latest")
    //            .withParam(
    //              TransactionParams()
    //                .withData(
    //                  wethAbi.balanceOf.pack(
    //                    BalanceOfFunction
    //                      .Parms(tx.get.from)
    //                  )
    //                )
    //                .withTo("0xef68e7c694f40c8202821edf525de3782458639f")
    //            )
    //      )
    //    )).mapTo[BatchCallContracts.Res]
    //      .map(_.resps.map(res ⇒ wethAbi.balanceOf.unpackResult(res.result)))
    //
    //    allowance ← (ethereumAccessActor ? EthCall.Req(tag = "latest")
    //      .withParam(
    //        TransactionParams()
    //          .withData(
    //            wethAbi.allowance.pack(
    //              AllowanceFunction
    //                .Parms(
    //                  _owner = "0xb94065482ad64d4c2b9252358d746b39e820a582",
    //                  _spender = delegateAdderess
    //                )
    //            )
    //          )
    //          .withTo("0xef68e7c694f40c8202821edf525de3782458639f")
    //      ))
    //      .mapTo[EthCall.Res]
    //      .map(resp ⇒ wethAbi.allowance.unpackResult(resp.result))
    //
    //    allowances ← (ethereumAccessActor ? BatchCallContracts.Req(
    //      batchTx.map(
    //        tx ⇒
    //          EthCall.Req(tag = "latest")
    //            .withParam(
    //              TransactionParams()
    //                .withData(
    //                  wethAbi.allowance.pack(
    //                    AllowanceFunction
    //                      .Parms(tx.get.from, _spender = delegateAdderess)
    //                  )
    //                )
    //                .withTo("0xef68e7c694f40c8202821edf525de3782458639f")
    //            )
    //      )
    //    )).mapTo[BatchCallContracts.Res]
    //      .map(_.resps.map(res ⇒ wethAbi.allowance.unpackResult(res.result)))
    //    uncle ← (ethereumAccessActor ? GetUncle.Req(
    //      blockNum = "0x69555e",
    //      index = "0x0"
    //    )).mapTo[GetBlockWithTxHashByHash.Res]
    //      .map(_.result.get)
    //    uncles ← (ethereumAccessActor ? BatchGetUncle.Req()
    //      .withReqs(
    //        Seq(
    //          GetUncle.Req(
    //            blockNum = "0x69555e",
    //            index = "0x0"
    //          )
    //        )
    //      ))
    //      .mapTo[BatchGetUncle.Res]
    //      .map(_.resps.map(_.result.get))
    //    gas ← (ethereumAccessActor ? GetEstimatedGas.Req(
    //      to = "0xef68e7c694f40c8202821edf525de3782458639f"
    //    ).withData(
    //      wethAbi.transfer.pack(
    //        TransferFunction.Parms(
    //          to = "0xb94065482ad64d4c2b9252358d746b39e820a582",
    //          amount = BigInt("10000")
    //        )
    //      )
    //    )).mapTo[GetEstimatedGas.Res]
    //      .map(_.result)
  } yield {
    println(s"BlockNum: ${blockNum} ---${Numeric.toBigInt(blockNum)}")
    //    println(s"txHashs:${blockWithTxHash}")
    //    println(s"txHashs:${blockByHashWithHash}")
    //    println(s"Txs:${blockWithTxObjcet}")
    //    println(s"Txs:${blockByHashwithObject}")
    //    println(s"Txs:${txs}")
    //    println(s"Receipts:${receipts}")
    //    println(s"Receipts:${batchReceipts.map(_.from)}")
    //    println(s"nonce:${nonce}")
    //    println(s"TxCount:${txCount}")
    //    println(s"txs:${batchTx.map(_.get.from)}")
    //    println(s"Lrc Balance:${lrcBalance.get.balance.toString()}")
    //    println(s"balances:${lrcbalances.map(_.get.balance.toString)}")
    //    println(s"Allowance:${allowance.get.allowance.toString}")
    //    println(s"Allowances:${allowances.map(_.get.allowance.toString)}")
    //    println(s"Uncle:${uncle}")
    //    println(s"Uncles:${uncles}")
    //    println(s"Gas:$gas")
    //    println("test success")
  }

  Await.result(fu, 2 minute)

}
