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

package org.loopring.lightcone.actors.core

import akka.actor.ActorRef
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.proto._
import akka.pattern._
import akka.util.Timeout
import org.loopring.lightcone.persistence.DatabaseModule

import scala.concurrent.Future
import scala.concurrent.duration._
import org.web3j.utils.Numeric

import scala.util.{Failure, Success}

trait EventExtraction {
  actor: ActorWithPathBasedConfig =>
  implicit val timeout: Timeout
  implicit val actors: Lookup[ActorRef]
  implicit val dispatchers: Seq[EventDispatcher[_]]
  implicit val dbModule: DatabaseModule
  var blockData: RawBlockData = _

  val GET_BLOCK = Notify("get_block")
  val RETRIEVE_RECEIPTS = Notify("retrieve_receipts")
  val PROCESS_EVENTS = Notify("process_events")

  var untilBlock = Long.MaxValue
  def ethereumAccessorActor = actors.get(EthereumAccessActor.name)

  def handleMessage: Receive = {
    case GET_BLOCK =>
      assert(blockData != null)

      getBlockData(blockData.height + 1).map {
        case Some(block) =>
          blockData = block
          self ! RETRIEVE_RECEIPTS
        case None =>
          context.system.scheduler
            .scheduleOnce(1 seconds, self, GET_BLOCK)
      }
    case RETRIEVE_RECEIPTS =>
      for {
        receipts <- getAllReceipts
      } yield {
        if (receipts.forall(_.nonEmpty)) {
          blockData = blockData.withReceipts(receipts.map(_.get))
          self ! PROCESS_EVENTS
        } else {
          context.system.scheduler
            .scheduleOnce(500 millis, self, RETRIEVE_RECEIPTS)
        }
      }
    case PROCESS_EVENTS =>
      processEvents onComplete {
        case Success(_) =>
          if (blockData.height < untilBlock) self ! GET_BLOCK
        case Failure(e) =>
          log.error(
            s" Actor: $name extracts ethereum events failed with error:${e.getMessage}"
          )
      }
  }

  def getBlockData(blockNum: Long): Future[Option[RawBlockData]] = {
    for {
      blockOpt <- (ethereumAccessorActor ? GetBlockWithTxObjectByNumber.Req(
        Numeric.toHexString(BigInt(blockNum).toByteArray)
      )).mapAs[GetBlockWithTxObjectByNumber.Res]
        .map(_.result)
      uncles <- if (blockOpt.isDefined && blockOpt.get.uncles.nonEmpty) {
        val batchGetUnclesReq = BatchGetUncle.Req(
          blockOpt.get.uncles.indices.map(
            index =>
              GetUncle.Req(
                blockOpt.get.number,
                Numeric.prependHexPrefix(index.toHexString)
              )
          )
        )
        (ethereumAccessorActor ? batchGetUnclesReq)
          .mapAs[BatchGetUncle.Res]
          .map(_.resps.map(_.result.get.miner))
      } else {
        Future.successful(Seq.empty)
      }
      rawBlock = blockOpt.map(
        block =>
          RawBlockData(
            hash = block.hash,
            height = Numeric.toBigInt(block.number).longValue(),
            timestamp = block.timestamp,
            miner = block.miner,
            uncles = uncles,
            txs = block.transactions
          )
      )
    } yield rawBlock
  }

  def getAllReceipts: Future[Seq[Option[TransactionReceipt]]] =
    (ethereumAccessorActor ? BatchGetTransactionReceipts.Req(
      blockData.txs
        .map(tx => GetTransactionReceipt.Req(tx.hash))
    )).mapAs[BatchGetTransactionReceipts.Res]
      .map(_.resps.map(_.result))

  def processEvents: Future[_] = {
    dispatchers.foreach(_.dispatch(blockData))
    dbModule.blockService.saveBlock(
      BlockData(
        hash = blockData.hash,
        height = blockData.height,
        timestamp = Numeric.toBigInt(blockData.timestamp).longValue()
      )
    )
  }
}