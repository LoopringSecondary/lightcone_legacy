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

package org.loopring.lightcone.indexer

import scala.io.Source
import scala.collection.JavaConverters._

import java.io.PrintWriter
import java.util.Timer
import java.util.TimerTask

import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterNumber
import org.web3j.protocol.core.methods.response.EthBlock
import org.web3j.protocol.core.methods.response.Transaction

// The Ethereum indexer
object Main {
  val blockNumFile = ".block-num"
  val infuraUrl = ""
  val rinkebyUrl = "https://rinkeby.infura.io/hM4sFGiBdqbnGTxk5YT2"

  def main(args: Array[String]): Unit = {
    val web3j = Web3j.build(new HttpService(rinkebyUrl))
    var fromBlockNumber = getFromBlockNumber
    var toBlockNumber = getLatestBlockNumber(web3j)
    val txHandler = new TransactionHandlerImpl

    val timer = new Timer
    val task = new TimerTask {
      def run = {
        if (toBlockNumber > fromBlockNumber) {
          processEthereumBlocks(web3j, fromBlockNumber, toBlockNumber, txHandler)
          fromBlockNumber = toBlockNumber + 1
        }

        toBlockNumber = getLatestBlockNumber(web3j)
      }
    }

    timer.schedule(task, 5000L, 1000L)
  }

  private def getFromBlockNumber: Long =
    try {
      val blockNumStr = Source.fromFile(blockNumFile).getLines.mkString.trim
      blockNumStr.toLong
    } catch {
      case _: Throwable ⇒ 0L
    }

  private def getLatestBlockNumber(web3j: Web3j): Long = {
    val ethBlockNumber = web3j.ethBlockNumber().send
    ethBlockNumber.getBlockNumber.longValue
  }

  private def processEthereumBlocks(
    web3j: Web3j,
    fromBlock: Long,
    toBlock: Long,
    txHandler: TransactionHandler
  ) {
    (fromBlock to toBlock).foreach(blockNum ⇒ {
      val blockParameter = new DefaultBlockParameterNumber(blockNum)
      val txsOfBlock = web3j
        .ethGetBlockByNumber(blockParameter, true)
        .send
        .getBlock
        .getTransactions
        .asScala

      txsOfBlock.foreach(tx ⇒ txHandler.handle(tx.get.asInstanceOf[Transaction]))

      new PrintWriter(blockNumFile) {
        try {
          write("" + blockNum)
        } finally {
          close
        }
      }

    })
  }

}
