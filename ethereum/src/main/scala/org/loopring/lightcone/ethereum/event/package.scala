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

package org.loopring.lightcone.ethereum

import com.google.protobuf.ByteString
import org.loopring.lightcone.ethereum.abi.OrdersCancelledEvent.Result
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.MarketHashProvider.convert2Hex
import org.loopring.lightcone.proto.{OrdersCancelledEvent, _}
import org.web3j.utils.Numeric

import scala.collection.mutable.ListBuffer

package object event {
  val erc20Abi = ERC20ABI()
  val wethAbi = WETHABI()
  val tradeHistoryAbi = TradeHistoryAbi()
  val ringSubmitterAbi = RingSubmitterAbi()
  val loopringProtocolAbi = LoopringProtocolAbi()

  def getBalanceAndAllowanceAddrs(
      txs: Seq[(Transaction, Option[TransactionReceipt])]
    )(
      implicit delegate: Address,
      protocol: Address
    ): (Seq[(String, String)], Seq[(String, String)]) = {
    val balanceAddresses = ListBuffer.empty[(String, String)]
    val allowanceAddresses = ListBuffer.empty[(String, String)]
    if (txs.forall(_._2.nonEmpty)) {
      txs.foreach(tx ⇒ {
        balanceAddresses.append(tx._2.get.from → Address.zeroAddress)
        if (Numeric.toBigInt(tx._2.get.status).intValue() == 1) {
          if (BigInt(Numeric.toBigInt(tx._1.value)) > 0) {
            balanceAddresses.append(tx._2.get.to → Address.zeroAddress)
          }
          wethAbi.unpackFunctionInput(tx._1.input) match {
            case Some(param: TransferFunction.Parms) ⇒
              balanceAddresses.append(
                tx._1.from → tx._1.to,
                param.to → tx._1.to
              )
            case Some(param: ApproveFunction.Parms) ⇒
              if (param.spender.equalsIgnoreCase(delegate.toString))
                allowanceAddresses.append(
                  tx._1.from → tx._1.to
                )
            case Some(param: TransferFromFunction.Parms) ⇒
              balanceAddresses.append(
                param.txFrom -> tx._1.to,
                param.to → tx._1.to
              )
            case _ ⇒
          }
        }
        tx._2.get.logs.foreach(log ⇒ {
          wethAbi.unpackEvent(log.data, log.topics.toArray) match {
            case Some(transfer: TransferEvent.Result) ⇒
              balanceAddresses.append(
                transfer.from → log.address,
                transfer.receiver → log.address
              )
              if (tx._2.get.to.equalsIgnoreCase(protocol.toString)) {
                allowanceAddresses.append(transfer.from → log.address)
              }
            case Some(approval: ApprovalEvent.Result) ⇒
              if (approval.spender.equalsIgnoreCase(delegate.toString))
                allowanceAddresses.append(approval.owner → log.address)
            case Some(deposit: DepositEvent.Result) ⇒
              balanceAddresses.append(deposit.dst → log.address)
            case Some(withdrawal: WithdrawalEvent.Result) ⇒
              balanceAddresses.append(withdrawal.src → log.address)
            case _ ⇒
          }
        })
      })
    }
    (balanceAddresses.toSet.toSeq, allowanceAddresses.toSet.toSeq)
  }

  implicit def bytes2ByteString(bytes: Array[Byte]): ByteString =
    ByteString.copyFrom(bytes)

}
