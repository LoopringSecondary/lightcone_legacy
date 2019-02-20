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

  log.info(s"chain-regorg-manager: maxDepth=$maxDepth strictMode=$strictMode")

  case class BlockData(
      val orderIds: Set[String] = Set.empty,
      val accounts: Map[String, Set[String]] = Map.empty) {

    def recordOrderUpdate(orderId: String): BlockData =
      copy(orderIds = orderIds + orderId)

    def recordAccountUpdate(
        address: String,
        token: String
      ): BlockData = {
      accounts.get(address) match {
        case Some(tokens) =>
          copy(accounts = accounts + (address -> (tokens + token)))
        case None =>
          copy(accounts = accounts + (address -> Set(token)))
      }
    }

    def +(that: BlockData): BlockData = {
      val orderIds = this.orderIds ++ that.orderIds
      val accounts = this.accounts ++ that.accounts.map {
        case (k, v) => k -> (v ++ this.accounts.getOrElse(k, Set.empty))
      }
      BlockData(orderIds, accounts)
    }
  }

  assert(maxDepth >= 10 && maxDepth <= 1000)

  private var blocks = SortedMap.empty[Long, BlockData]

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

    val aggregated = expiredBlocks.values.reduce(_ + _)

    val orderIds = aggregated.orderIds.toSeq
    val accounts = aggregated.accounts.map {
      case (address, tokens) =>
        ChainReorganizationImpact.AccountInfo(address, tokens.toSeq)
    }.toSeq

    val impact = ChainReorganizationImpact(orderIds, accounts)

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
    ) =
    checkBlockIdxTo(blockIdx) {
      updateBlockData(blockIdx, _.recordOrderUpdate(orderId))
    }

  def recordAccountUpdate(
      blockIdx: Long,
      address: String,
      token: String
    ) = checkBlockIdxTo(blockIdx) {
    updateBlockData(blockIdx, _.recordAccountUpdate(address, token))
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

  private def updateBlockData(
      blockIdx: Long,
      update: BlockData => BlockData
    ) =
    blocks.get(blockIdx) match {
      case Some(block) =>
        blocks += blockIdx -> update(block)
      case None =>
        if (blocks.size == maxDepth) {
          blocks = blocks.tail
        }
        val block = update(new BlockData())
        blocks += blockIdx -> block

        log.debug(
          s"history size: ${blocks.size} with latest block index: " +
            blocks.lastOption.map(_._1).getOrElse(0L)
        )
    }
}
