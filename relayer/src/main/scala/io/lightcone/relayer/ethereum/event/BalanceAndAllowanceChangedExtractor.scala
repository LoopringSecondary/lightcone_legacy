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
import io.lightcone.ethereum.event
import io.lightcone.lib.{Address, NumericConversion}
import io.lightcone.relayer.base.Lookup
import io.lightcone.relayer.data._
import io.lightcone.relayer.ethereum._
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
    extends AbstractEventExtractor
    with TransferEventSupport
    with ApprovalEventSupport
    with BalanceUpdatedSuppot {

  val protocolConf = config.getConfig("loopring_protocol")
  val delegateAddress = Address(protocolConf.getString("delegate-address"))
  val protocolAddress = Address(protocolConf.getString("protocol-address"))

  def wethAddress =
    Address(
      metadataManager
        .getTokenWithSymbol("weth")
        .get
        .metadata
        .getOrElse(
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            s"not found metadata of token WETH"
          )
        )
        .address
    )
  @inline def ethereumAccessor = actors.get(EthereumAccessActor.name)

  def extractEventsFromTx(
      tx: Transaction,
      receipt: TransactionReceipt,
      eventHeader: event.EventHeader
    ): Future[Seq[AnyRef]] =
    for {
      balanceEvents <- extractTransferEvents(tx, receipt, eventHeader)
      allowanceEvents <- extractApprovalEvent(tx, receipt, eventHeader)
    } yield balanceEvents ++ allowanceEvents

  override def extractEvents(block: RawBlockData): Future[Seq[AnyRef]] = {
    for {
      changedEvents1 <- super.extractEvents(block)
      changedEvents2 <- extractEventOfMiner(event.BlockHeader())
      changedEvents = changedEvents1 ++ changedEvents2
      eventsWithState <- Future.sequence(
        changedEvents.map(extractEventWithState)
      )
    } yield changedEvents ++ eventsWithState.flatten
  }

}

trait ApprovalEventSupport {
  extractor: BalanceAndAllowanceChangedExtractor =>

  def extractApprovalEvent(
      tx: Transaction,
      receipt: TransactionReceipt,
      eventHeader: event.EventHeader
    ): Future[Seq[AnyRef]] = Future {
    val approvalEvents = ListBuffer.empty[event.ApprovalEvent]
    receipt.logs.foreach { log =>
      wethAbi.unpackEvent(log.data, log.topics.toArray) match {
        case Some(transfer: TransferEvent.Result)
            if Address(receipt.to).equals(protocolAddress) =>
          approvalEvents.append(
            event.ApprovalEvent(
              header = Some(eventHeader),
              owner = transfer.from,
              spender = delegateAddress.toString(),
              token = log.address,
              amount = transfer.amount
            )
          )

        case Some(approval: ApprovalEvent.Result)
            if Address(approval.spender).equals(delegateAddress) =>
          approvalEvents.append(
            event.ApprovalEvent(
              header = Some(eventHeader),
              owner = approval.owner,
              spender = delegateAddress.toString(),
              token = log.address,
              amount = approval.amount
            )
          )
        case _ =>
      }
    }
    if (isSucceed(receipt.status)) {
      wethAbi.unpackFunctionInput(tx.input) match {
        case Some(param: ApproveFunction.Parms)
            if Address(param.spender).equals(delegateAddress) =>
          approvalEvents.append(
            event.ApprovalEvent(
              header = Some(eventHeader),
              owner = tx.from,
              spender = delegateAddress.toString(),
              token = tx.to,
              amount = param.amount
            )
          )
        case _ =>
      }
    }
    approvalEvents
  }

}

trait TransferEventSupport {
  extractor: BalanceAndAllowanceChangedExtractor =>

  def extractTransferEvents(
      tx: Transaction,
      receipt: TransactionReceipt,
      eventHeader: event.EventHeader
    ): Future[Seq[AnyRef]] = Future {
    val txValue = NumericConversion.toBigInt(tx.value)
    val transfers = ListBuffer.empty[event.TransferEvent]
    if (isSucceed(receipt.status)) {
      receipt.logs.zipWithIndex.foreach {
        case (log, index) =>
          wethAbi.unpackEvent(log.data, log.topics.toArray) match {
            case Some(transfer: TransferEvent.Result) =>
              transfers.append(
                event.TransferEvent(
                  Some(eventHeader.withLogIndex(index)),
                  from = transfer.from,
                  to = transfer.receiver,
                  token = log.address,
                  amount = transfer.amount
                )
              )
            case Some(withdraw: WithdrawalEvent.Result) =>
              transfers.append(
                event.TransferEvent(
                  Some(eventHeader.withLogIndex(index)),
                  from = withdraw.src,
                  to = log.address,
                  token = log.address,
                  amount = withdraw.wad
                ),
                event.TransferEvent(
                  Some(eventHeader.withLogIndex(index)),
                  from = log.address,
                  to = withdraw.src,
                  token = Address.ZERO.toString(),
                  amount = withdraw.wad
                )
              )
            case Some(deposit: DepositEvent.Result) =>
              transfers.append(
                event.TransferEvent(
                  Some(eventHeader.withLogIndex(index)),
                  from = log.address,
                  to = deposit.dst,
                  token = log.address,
                  amount = deposit.wad
                ),
                event.TransferEvent(
                  Some(eventHeader.withLogIndex(index)),
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
          event.TransferEvent(
            header = Some(eventHeader),
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
            event.TransferEvent(
              header = Some(eventHeader),
              from = tx.from,
              to = transfer.to,
              token = tx.to,
              amount = transfer.amount
            )
          )
        case Some(transferFrom: TransferFromFunction.Parms) =>
          transfers.append(
            event.TransferEvent(
              header = Some(eventHeader),
              from = transferFrom.txFrom,
              to = transferFrom.to,
              token = tx.to,
              amount = transferFrom.amount
            )
          )
        case Some(_: DepositFunction.Parms) =>
          transfers.append(
            event.TransferEvent(
              header = Some(eventHeader),
              from = tx.to,
              to = tx.from,
              token = tx.to,
              amount = txValue
            ),
            event.TransferEvent(
              header = Some(eventHeader),
              from = tx.from,
              to = tx.to,
              token = Address.ZERO.toString(),
              amount = txValue
            )
          )
        case Some(withdraw: WithdrawFunction.Parms) =>
          transfers.append(
            event.TransferEvent(
              header = Some(eventHeader),
              from = tx.from,
              to = tx.to,
              token = tx.to,
              amount = withdraw.wad
            ),
            event.TransferEvent(
              header = Some(eventHeader),
              from = tx.to,
              to = tx.from,
              token = Address.ZERO.toString(),
              amount = withdraw.wad
            )
          )
        case _ =>
          if (txValue > 0) {
            transfers.append(
              event.TransferEvent(
                header = Some(eventHeader),
                from = tx.from,
                to = tx.to,
                token = Address.ZERO.toString(),
                amount = txValue
              )
            )
            if (Address(tx.to).equals(wethAddress)) {
              transfers.append(
                event.TransferEvent(
                  header = Some(eventHeader),
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
            owner = Address.normalize(event.to),
            header = event.header.map(_.withEventIndex(1))
          )
        )
    )
  }

  def extractEventOfMiner(blockHeader: event.BlockHeader): Future[Seq[AnyRef]] =
    Future {
      //TODO: 需要确定奖励金额以及txhash等值
      //    blockHeader.uncles
      //      .+:(blockHeader.miner)
      //      .map(
      //        addr =>
      //          event.TransferEvent(
      //            header = Some(EventHeader(txHash="", txStatus = TxStatus.TX_STATUS_SUCCESS, blockHeader=Some(blockHeader))),
      //            owner = addr,
      //            from = Address.ZERO.toString(),
      //            to = addr,
      //            token = Address.ZERO.toString()
      ////            amount =
      //          )
      //      )
      Seq.empty
    }

}

trait BalanceUpdatedSuppot {
  extractor: BalanceAndAllowanceChangedExtractor =>

  def extractEventWithState(evt: AnyRef): Future[Seq[AnyRef]] = {
    var balanceAddresses = Set.empty[event.AddressBalanceUpdatedEvent]
    var allowanceAddresses = Set.empty[event.AddressAllowanceUpdatedEvent]
    evt match {
      case transfer: event.TransferEvent =>
        balanceAddresses = balanceAddresses ++ Set(
          event.AddressBalanceUpdatedEvent(
            transfer.getHeader.txFrom,
            Address.ZERO.toString()
          ),
          event.AddressBalanceUpdatedEvent(transfer.from, transfer.token),
          event.AddressBalanceUpdatedEvent(transfer.to, transfer.token)
        )
        if (Address(transfer.getHeader.txTo).equals(protocolAddress))
          allowanceAddresses = allowanceAddresses ++ Set(
            event.AddressAllowanceUpdatedEvent(transfer.from, transfer.token)
          )
      case approval: event.ApprovalEvent
          if Address(approval.spender).equals(delegateAddress) =>
        allowanceAddresses = allowanceAddresses ++ Set(
          event.AddressAllowanceUpdatedEvent(approval.owner, approval.token)
        )
      case _ =>
    }

    for {
      allowanceEvents <- batchGetAllowances(allowanceAddresses.toSeq)
      balanceEvents <- batchGetBalances(balanceAddresses.toSeq)
    } yield balanceEvents ++ allowanceEvents
  }

  def batchGetAllowances(
      allowanceAddresses: Seq[event.AddressAllowanceUpdatedEvent]
    ) =
    for {
      tokenAllowances <- if (allowanceAddresses.nonEmpty) {
        val batchCallReq =
          brb.buildRequest(delegateAddress, allowanceAddresses, "latest")
        (ethereumAccessor ? batchCallReq)
          .mapAs[BatchCallContracts.Res]
          .map(
            resp =>
              resp.resps
                .map(
                  res =>
                    Amount(NumericConversion.toBigInt(res.result), resp.block)
                )
          )
      } else {
        Future.successful(Seq.empty)
      }
    } yield {
      (allowanceAddresses zip tokenAllowances).map(item => {
        event.AddressAllowanceUpdatedEvent(
          address = Address.normalize(item._1.address),
          token = Address.normalize(item._1.token),
          allowance = Some(item._2)
        )
      })
    }

  def batchGetBalances(
      balanceAddresses: Seq[event.AddressBalanceUpdatedEvent]
    ) = {
    val (ethAddress, tokenAddresses) =
      balanceAddresses.partition(addr => Address(addr.token).isZero)
    val batchCallReq = brb.buildRequest(tokenAddresses, "latest")
    for {
      tokenBalances <- if (tokenAddresses.nonEmpty) {
        (ethereumAccessor ? batchCallReq)
          .mapAs[BatchCallContracts.Res]
          .map(
            resp =>
              resp.resps
                .map(
                  res =>
                    Amount(NumericConversion.toBigInt(res.result), resp.block)
                )
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
            resp =>
              resp.resps
                .map(
                  res =>
                    Amount(NumericConversion.toBigInt(res.result), resp.block)
                )
          )
      } else {
        Future.successful(Seq.empty)
      }
    } yield {
      (tokenAddresses zip tokenBalances).map(
        item =>
          item._1.copy(
            address = Address.normalize(item._1.address),
            token = Address.normalize(item._1.token),
            balance = Some(item._2)
          )
      ) ++
        (ethAddress zip ethBalances).map(
          item =>
            event.AddressBalanceUpdatedEvent(
              address = Address.normalize(item._1.address),
              token = Address.normalize(item._1.token),
              balance = Some(item._2)
            )
        )
    }
  }
}
