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

package io.lightcone.relayer.ethereum.event
import akka.actor.ActorRef
import akka.pattern._
import io.lightcone.relayer.base._
import akka.util.Timeout
import com.google.inject.Inject
import com.typesafe.config.Config
import io.lightcone.ethereum.abi._
import io.lightcone.ethereum.event.{TransferEvent => PTransferEvent, _}
import io.lightcone.lib.{Address, NumericConversion}
import io.lightcone.relayer.base.Lookup
import io.lightcone.relayer.data.{
  BatchCallContracts,
  BatchGetEthBalance,
  EthGetBalance,
  RawBlockData,
  Transaction,
  TransactionReceipt
}
import io.lightcone.relayer.ethereum.{
  EthereumAccessActor,
  EthereumBatchCallRequestBuilder
}
import io.lightcone.core._

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

class BalanceAndAllowanceChangedExtractor @Inject()(
    implicit
    val config: Config,
    val brb: EthereumBatchCallRequestBuilder,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val ec: ExecutionContext,
    val metadataManager: MetadataManager)
    extends EventExtractorAlt {

  val protocolConf = config.getConfig("loopring_protocol")
  val delegateAddress = Address(protocolConf.getString("delegate-address"))
  val protocolAddress = Address(protocolConf.getString("protocol-address"))

  val wethAddress = Address(
    metadataManager.getTokenWithSymbol("weth").get.meta.address
  )
  def ethereumAccessor = actors.get(EthereumAccessActor.name)

  def extractTx(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockHeader: BlockHeader
    ): Future[Seq[Any]] =
    for {
      balanceEvents <- extractBalance(tx, receipt, blockHeader)
      allowanceEvents <- extractAllowance(tx, receipt, blockHeader)
    } yield balanceEvents ++ allowanceEvents

  def extractBalance(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockHeader: BlockHeader
    ): Future[Seq[Any]] = Future {
    val header = getEventHeader(tx, receipt, blockHeader)
    val txValue = NumericConversion.toBigInt(tx.value)
    val transfers = ListBuffer.empty[PTransferEvent]
    if (isSucceed(receipt.status)) {
      receipt.logs.zipWithIndex.foreach {
        case (log, index) =>
          wethAbi.unpackEvent(log.data, log.topics.toArray) match {
            case Some(transfer: TransferEvent.Result) =>
              transfers.append(
                PTransferEvent(
                  Some(header.withLogIndex(index)),
                  from = transfer.from,
                  to = transfer.receiver,
                  token = log.address,
                  amount = transfer.amount
                )
              )
            case Some(withdraw: WithdrawalEvent.Result) =>
              transfers.append(
                PTransferEvent(
                  Some(header.withLogIndex(index)),
                  from = withdraw.src,
                  to = log.address,
                  token = log.address,
                  amount = withdraw.wad
                ),
                PTransferEvent(
                  Some(header.withLogIndex(index)),
                  from = log.address,
                  to = withdraw.src,
                  token = Address.ZERO.toString(),
                  amount = withdraw.wad
                )
              )
            case Some(deposit: DepositEvent.Result) =>
              transfers.append(
                PTransferEvent(
                  Some(header.withLogIndex(index)),
                  from = log.address,
                  to = deposit.dst,
                  token = log.address,
                  amount = deposit.wad
                ),
                PTransferEvent(
                  Some(header.withLogIndex(index)),
                  from = deposit.dst,
                  to = log.address,
                  token = Address.ZERO.toString(),
                  amount = deposit.wad
                )
              )
            case _ =>
          }
      }
      if (txValue > 0 && !Address(tx.to).equals(wethAddress)) {
        transfers.append(
          PTransferEvent(
            header = Some(header),
            from = tx.from,
            to = tx.to,
            token = Address.ZERO.toString(),
            amount = txValue
          )
        )
      }
    } else {
      wethAbi.unpackFunctionInput(tx.input) match {
        case Some(transfer: TransferFunction.Parms) =>
          transfers.append(
            PTransferEvent(
              header = Some(header),
              from = tx.from,
              to = transfer.to,
              token = tx.to,
              amount = transfer.amount
            )
          )
        case Some(transferFrom: TransferFromFunction.Parms) =>
          transfers.append(
            PTransferEvent(
              header = Some(header),
              from = transferFrom.txFrom,
              to = transferFrom.to,
              token = tx.to,
              amount = transferFrom.amount
            )
          )
        case Some(_: DepositFunction.Parms) =>
          transfers.append(
            PTransferEvent(
              header = Some(header),
              from = tx.to,
              to = tx.from,
              token = tx.to,
              amount = txValue
            ),
            PTransferEvent(
              header = Some(header),
              from = tx.from,
              to = tx.to,
              token = Address.ZERO.toString(),
              amount = txValue
            )
          )
        case Some(withdraw: WithdrawFunction.Parms) =>
          transfers.append(
            PTransferEvent(
              header = Some(header),
              from = tx.from,
              to = tx.to,
              token = tx.to,
              amount = withdraw.wad
            ),
            PTransferEvent(
              header = Some(header),
              from = tx.to,
              to = tx.from,
              token = Address.ZERO.toString(),
              amount = withdraw.wad
            )
          )
        case _ =>
          if (txValue > 0) {
            transfers.append(
              PTransferEvent(
                header = Some(header),
                from = tx.from,
                to = tx.to,
                token = Address.ZERO.toString(),
                amount = txValue
              )
            )
            if (Address(tx.to).equals(wethAddress)) {
              transfers.append(
                PTransferEvent(
                  header = Some(header),
                  from = tx.to,
                  to = tx.from,
                  token = tx.to,
                  amount = txValue
                )
              )
            }
          }
      }
    }
    transfers.flatMap(
      event =>
        Seq(
          event.copy(
            from = Address.normalize(event.from),
            to = Address.normalize(event.to),
            token = Address.normalize(event.token),
            owner = Address.normalize(event.from),
            header = event.header.map(_.withEventIndex(0))
          ),
          event.copy(
            from = Address.normalize(event.from),
            to = Address.normalize(event.to),
            token = Address.normalize(event.token),
            owner = event.to,
            header = event.header.map(_.withEventIndex(1))
          )
        )
    )
  }

  def extractAllowance(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockHeader: BlockHeader
    ): Future[Seq[Any]] = ???

  def extractEventOfMiner(blockHeader: BlockHeader): Future[Seq[Any]] = {
    Future.successful(Seq.empty)
  }

  override def extractBlock(block: RawBlockData): Future[Seq[Any]] =
    for {
      changedEvents1 <- super.extractBlock(block)
      changedEvents2 <- extractEventOfMiner(BlockHeader())
      changedEvents = changedEvents1 ++ changedEvents2
      eventsWithState <- Future.sequence(
        changedEvents.map(extractEventWithState)
      )
    } yield changedEvents ++ eventsWithState

  def extractEventWithState(evt: Any): Future[Seq[Any]] = {
    var balanceAddresses = Set.empty[AddressBalanceUpdatedEvent]
    var allowanceAddresses = Set.empty[AddressAllowanceUpdatedEvent]

    evt match {
      case Some(transfer: PTransferEvent) =>
        balanceAddresses = balanceAddresses ++ Set(
          AddressBalanceUpdatedEvent(
            transfer.getHeader.txFrom,
            Address.ZERO.toString()
          ),
          AddressBalanceUpdatedEvent(transfer.from),
          AddressBalanceUpdatedEvent(transfer.to, transfer.token)
        )
        if (Address(transfer.getHeader.txFrom).equals(protocolAddress))
          allowanceAddresses = allowanceAddresses ++ Set(
            AddressAllowanceUpdatedEvent(transfer.from, transfer.token)
          )
      case Some(approval: ApprovalEvent.Result)
          if Address(approval.spender).equals(delegateAddress) =>
//          allowanceAddresses.append(
//            AddressAllowanceUpdatedEvent(approval.owner, )
//          )
      case _ =>
    }

    for {
      allowanceEvents <- batchGetAllowances(allowanceAddresses.toSeq)
      balanceEvents <- batchGetBalances(balanceAddresses.toSeq)
    } yield balanceEvents ++ allowanceEvents
  }

  def batchGetAllowances(
      allowanceAddresses: Seq[AddressAllowanceUpdatedEvent]
    ) =
    for {
      tokenAllowances <- if (allowanceAddresses.nonEmpty) {
        val batchCallReq =
          brb.buildRequest(delegateAddress, allowanceAddresses, "latest")
        (ethereumAccessor ? batchCallReq)
          .mapAs[BatchCallContracts.Res]
          .map(
            _.resps
              .map(res => NumericConversion.toBigInt(res.result))
          )
      } else {
        Future.successful(Seq.empty)
      }
    } yield {
      (allowanceAddresses zip tokenAllowances).map(
        item =>
          AddressAllowanceUpdatedEvent(
            address = Address.normalize(item._1.address),
            token = Address.normalize(item._1.token),
            allowance = item._2
          )
      )
    }

  def batchGetBalances(balanceAddresses: Seq[AddressBalanceUpdatedEvent]) = {
    val (ethAddress, tokenAddresses) =
      balanceAddresses.partition(addr => Address(addr.token).isZero)
    val batchCallReq = brb.buildRequest(tokenAddresses, "latest")
    for {
      tokenBalances <- if (tokenAddresses.nonEmpty) {
        (ethereumAccessor ? batchCallReq)
          .mapAs[BatchCallContracts.Res]
          .map(
            _.resps
              .map(res => NumericConversion.toBigInt(res.result))
          )
      } else {
        Future.successful(Seq.empty)
      }
      ethBalances <- if (ethAddress.nonEmpty) {
        (ethereumAccessor ? BatchGetEthBalance
          .Req(
            ethAddress.map(addr => EthGetBalance.Req(address = addr.address))
          ))
          .mapAs[BatchGetEthBalance.Res]
          .map(
            _.resps
              .map(res => NumericConversion.toBigInt(res.result))
          )
      } else {
        Future.successful(Seq.empty)
      }
    } yield {
      (tokenAddresses zip tokenBalances).map(
        item => item._1.withBalance(item._2)
      ) ++
        (ethAddress zip ethBalances).map(
          item =>
            AddressBalanceUpdatedEvent(
              address = Address.normalize(item._1.address),
              token = Address.normalize(item._1.token),
              balance = item._2
            )
        )
    }

  }

}
