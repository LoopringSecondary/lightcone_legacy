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

package io.lightcone.core

import org.slf4s.Logging
import scala.collection.SortedMap

// Owner: dongw

// This class is not thread-safe
class ChainReorganizationManagerImpl(
    val maxDepth: Int = 100,
    val strictMode: Boolean = false)
    extends ChainReorganizationManager
    with Logging {

  assert(maxDepth >= 10 && maxDepth <= 1000)

  class BlockTrackingData() {
    var orderIds = Set.empty[String]
    var accounts = Map.empty[String, Set[String]]

    def recordOrderUpdate(orderId: String) = {
      orderIds += orderId
    }

    def recordAccountUpdate(
        address: String,
        token: String
      ) = accounts.get(address) match {
      case Some(tokens) => accounts += address -> (tokens + token)
      case None         => accounts += address -> Set(token)
    }

    def mergeWith(another: BlockTrackingData): BlockTrackingData = {
      orderIds ++= another.orderIds
      another.accounts.foreach {
        case (address, tokens) =>
          accounts.get(address) match {
            case Some(tokens_) => accounts += (address -> (tokens_ ++ tokens))
            case None          => accounts += (address -> tokens)
          }
      }
      this
    }
  }

  private var blocks = SortedMap.empty[Long, BlockTrackingData]

  def reorganizedAt(blockIdx: Long): ChainReorganizationImpact = {

    blocks.headOption foreach {
      case (idx, _) if blockIdx < idx =>
        log.error(
          s"block reorgnaized at a block index ($blockIdx) smaller than the" +
            s"minimal knonw block ($idx)"
        )
    }
    val (remainingBlocks, expiredBlocks) = blocks.partition(_._1 < blockIdx)
    blocks = remainingBlocks

    val aggregated = new BlockTrackingData()
    expiredBlocks.values.foreach(aggregated.mergeWith)

    var impact =
      ChainReorganizationImpact(
        aggregated.orderIds.toSeq,
        aggregated.accounts.map {
          case (address, tokens) =>
            ChainReorganizationImpact.AccountInfo(address, tokens.toSeq)
        }.toSeq
      )

    log.info(
      s"reorged at $blockIdx: ${impact.orderIds.size} orders and " +
        s"${impact.accounts.size} accounts impacted, " +
        s"new history size: ${blocks.size}"
    )

    impact
  }

  def reset(): Unit = {
    blocks = SortedMap.empty
  }

  def recordOrderUpdate(
      blockIdx: Long,
      orderId: String
    ) = checkBlockIdxTo(blockIdx) {
    getBlockTrackingData(blockIdx)
      .recordOrderUpdate(orderId)
  }

  def recordAccountUpdate(
      blockIdx: Long,
      address: String,
      token: String
    ) = checkBlockIdxTo(blockIdx) {
    getBlockTrackingData(blockIdx)
      .recordAccountUpdate(address, token)
  }

  private def checkBlockIdxTo(blockIdx: Long)(call: => Unit): Unit = {
    val lastKnownBlock = blocks.lastOption.map(_._1).getOrElse(0L)
    if (blockIdx >= lastKnownBlock) call
    else if (strictMode) {
      log.error(
        s"failed to record for a previous block $blockIdx vs $lastKnownBlock (last known block)"
      )
    } else {
      log.warn(
        s"record for a previous block $blockIdx vs $lastKnownBlock (last known block)"
      )
      call
    }
  }

  private def getBlockTrackingData(blockIdx: Long) =
    blocks.get(blockIdx) match {
      case Some(block) => block
      case None =>
        val block = new BlockTrackingData()

        if (blocks.size == maxDepth) {
          blocks = blocks.tail
        }
        blocks += blockIdx -> block

        log.debug(
          s"history size: ${blocks.size} with latest block index: " +
            blocks.lastOption.map(_._1).getOrElse(0L)
        )
        block
    }

}
