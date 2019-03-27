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

package io.lightcone.relayer.integration.helper

import io.lightcone.ethereum.TxStatus
import io.lightcone.ethereum.event.{AddressBalanceUpdatedEvent, BlockEvent}
import io.lightcone.ethereum.persistence.{Activity, TxEvents}
import io.lightcone.lib.Address
import io.lightcone.lib.NumericConversion.toAmount
import io.lightcone.relayer.integration.Metadatas._
import io.lightcone.relayer.integration._

trait ActivityHelper {

  def ethTransferPendingActivities(
      from: String,
      to: String,
      blockNumber: Long,
      txHash: String,
      transferAmount: BigInt,
      nonce: Long
    ) = {
    Seq(
      TxEvents(
        TxEvents.Events.Activities(
          TxEvents.Activities(
            Seq(
              Activity(
                owner = from,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_TRANSFER_OUT,
                timestamp = timeProvider.getTimeSeconds,
                token = Address.ZERO.toString(),
                detail = Activity.Detail.EtherTransfer(
                  Activity.EtherTransfer(
                    from,
                    Some(
                      toAmount(transferAmount)
                    )
                  )
                ),
                nonce = nonce
              ),
              Activity(
                owner = to,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_TRANSFER_IN,
                timestamp = timeProvider.getTimeSeconds,
                token = Address.ZERO.toString(),
                detail = Activity.Detail.EtherTransfer(
                  Activity.EtherTransfer(
                    to,
                    Some(
                      toAmount(transferAmount)
                    )
                  )
                ),
                nonce = nonce
              )
            )
          )
        )
      )
    )
  }

  def tokenTransferPendingActivities(
      from: String,
      to: String,
      blockNumber: Long,
      txHash: String,
      tokenAddress: String,
      transferAmount: BigInt,
      nonce: Long
    ) = {
    Seq(
      TxEvents(
        TxEvents.Events.Activities(
          TxEvents.Activities(
            Seq(
              Activity(
                owner = from,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.TOKEN_TRANSFER_OUT,
                timestamp = timeProvider.getTimeSeconds,
                token = tokenAddress,
                detail = Activity.Detail.TokenTransfer(
                  Activity.TokenTransfer(
                    from,
                    tokenAddress,
                    Some(
                      toAmount(transferAmount)
                    )
                  )
                ),
                nonce = nonce
              ),
              Activity(
                owner = to,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.TOKEN_TRANSFER_IN,
                timestamp = timeProvider.getTimeSeconds,
                token = tokenAddress,
                detail = Activity.Detail.TokenTransfer(
                  Activity.TokenTransfer(
                    to,
                    tokenAddress,
                    Some(
                      toAmount(transferAmount)
                    )
                  )
                ),
                nonce = nonce
              )
            )
          )
        )
      )
    )
  }

  def wrapWethPendingActivities(
      owner: String,
      blockNumber: Long,
      txHash: String,
      convertAmount: BigInt,
      nonce: Long
    ) = {
    Seq(
      TxEvents(
        TxEvents.Events.Activities(
          TxEvents.Activities(
            Seq(
              Activity(
                owner = owner,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_WRAP,
                timestamp = timeProvider.getTimeSeconds,
                token = Address.ZERO.toString(),
                detail = Activity.Detail.EtherConversion(
                  Activity.EtherConversion(
                    Some(
                      toAmount(convertAmount)
                    )
                  )
                ),
                nonce = nonce
              ),
              Activity(
                owner = owner,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_WRAP,
                timestamp = timeProvider.getTimeSeconds,
                token = WETH_TOKEN.address,
                detail = Activity.Detail.EtherConversion(
                  Activity.EtherConversion(
                    Some(
                      toAmount(convertAmount)
                    )
                  )
                ),
                nonce = nonce
              )
            )
          )
        )
      )
    )
  }

  def unwrapWethPendingActivities(
      owner: String,
      blockNumber: Long,
      txHash: String,
      convertAmount: BigInt,
      nonce: Long
    ) = {
    Seq(
      TxEvents(
        TxEvents.Events.Activities(
          TxEvents.Activities(
            Seq(
              Activity(
                owner = owner,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_UNWRAP,
                timestamp = timeProvider.getTimeSeconds,
                token = WETH_TOKEN.address,
                detail = Activity.Detail.EtherConversion(
                  Activity.EtherConversion(
                    Some(
                      toAmount(convertAmount)
                    )
                  )
                ),
                nonce = nonce
              ),
              Activity(
                owner = owner,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_UNWRAP,
                timestamp = timeProvider.getTimeSeconds,
                token = Address.ZERO.toString(),
                detail = Activity.Detail.EtherConversion(
                  Activity.EtherConversion(
                    Some(
                      toAmount(convertAmount)
                    )
                  )
                ),
                nonce = nonce
              )
            )
          )
        )
      )
    )
  }

  def blockConfirmedEvent(
      owner: String,
      blockNumber: Long,
      txHash: String,
      nonce: Long
    ) = {
    BlockEvent(
      blockNumber = blockNumber,
      txs = Seq(
        BlockEvent.Tx(
          from = owner,
          nonce = nonce,
          txHash = txHash
        )
      )
    )
  }

  def tokenTransferConfirmedActivities(
      from: String,
      to: String,
      blockNumber: Long,
      txHash: String,
      tokenAddress: String,
      transferAmount: BigInt,
      nonce: Long,
      outBalanceTo: BigInt,
      inBalanceTo: BigInt
    ) = {
    Seq(
      TxEvents(
        TxEvents.Events.Activities(
          TxEvents.Activities(
            Seq(
              Activity(
                owner = from,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.TOKEN_TRANSFER_OUT,
                timestamp = timeProvider.getTimeSeconds,
                token = tokenAddress,
                detail = Activity.Detail.TokenTransfer(
                  Activity.TokenTransfer(
                    from,
                    tokenAddress,
                    Some(
                      toAmount(transferAmount)
                    )
                  )
                ),
                nonce = nonce,
                txStatus = TxStatus.TX_STATUS_SUCCESS
              ),
              Activity(
                owner = to,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.TOKEN_TRANSFER_IN,
                timestamp = timeProvider.getTimeSeconds,
                token = tokenAddress,
                detail = Activity.Detail.TokenTransfer(
                  Activity.TokenTransfer(
                    to,
                    tokenAddress,
                    Some(
                      toAmount(transferAmount)
                    )
                  )
                ),
                nonce = nonce,
                txStatus = TxStatus.TX_STATUS_SUCCESS
              )
            )
          )
        )
      ),
      AddressBalanceUpdatedEvent(
        address = from,
        token = tokenAddress,
        balance = Some(
          toAmount(outBalanceTo)
        ),
        block = blockNumber
      ),
      AddressBalanceUpdatedEvent(
        address = to,
        token = tokenAddress,
        balance = Some(
          toAmount(inBalanceTo)
        ),
        block = blockNumber
      )
    )
  }

  def ethTransferConfirmedActivities(
      from: String,
      to: String,
      blockNumber: Long,
      txHash: String,
      transferAmount: BigInt,
      nonce: Long,
      outBalanceTo: BigInt,
      inBalanceTo: BigInt
    ) = {
    Seq(
      TxEvents(
        TxEvents.Events.Activities(
          TxEvents.Activities(
            Seq(
              Activity(
                owner = from,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_TRANSFER_OUT,
                timestamp = timeProvider.getTimeSeconds,
                token = Address.ZERO.toString(),
                detail = Activity.Detail.EtherTransfer(
                  Activity.EtherTransfer(
                    from,
                    Some(
                      toAmount(transferAmount)
                    )
                  )
                ),
                nonce = nonce,
                txStatus = TxStatus.TX_STATUS_SUCCESS
              ),
              Activity(
                owner = to,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_TRANSFER_IN,
                timestamp = timeProvider.getTimeSeconds,
                token = Address.ZERO.toString(),
                detail = Activity.Detail.EtherTransfer(
                  Activity.EtherTransfer(
                    to,
                    Some(
                      toAmount(transferAmount)
                    )
                  )
                ),
                nonce = nonce,
                txStatus = TxStatus.TX_STATUS_SUCCESS
              )
            )
          )
        )
      ),
      AddressBalanceUpdatedEvent(
        address = from,
        token = Address.ZERO.toString(),
        balance = Some(
          toAmount(outBalanceTo)
        ),
        block = blockNumber
      ),
      AddressBalanceUpdatedEvent(
        address = to,
        token = Address.ZERO.toString(),
        balance = Some(
          toAmount(inBalanceTo)
        ),
        block = blockNumber
      )
    )
  }

  def wethWrapConfirmedActivities(
      owner: String,
      blockNumber: Long,
      txHash: String,
      convertAmount: BigInt,
      nonce: Long,
      ethBalanceTo: BigInt,
      wethBalanceTo: BigInt
    ) = {
    Seq(
      TxEvents(
        TxEvents.Events.Activities(
          TxEvents.Activities(
            Seq(
              Activity(
                owner = owner,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_WRAP,
                timestamp = timeProvider.getTimeSeconds,
                token = Address.ZERO.toString(),
                detail = Activity.Detail.EtherConversion(
                  Activity.EtherConversion(
                    Some(
                      toAmount(convertAmount)
                    )
                  )
                ),
                nonce = nonce,
                txStatus = TxStatus.TX_STATUS_SUCCESS
              ),
              Activity(
                owner = owner,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_WRAP,
                timestamp = timeProvider.getTimeSeconds,
                token = WETH_TOKEN.address,
                detail = Activity.Detail.EtherConversion(
                  Activity.EtherConversion(
                    Some(
                      toAmount(convertAmount)
                    )
                  )
                ),
                nonce = nonce,
                txStatus = TxStatus.TX_STATUS_SUCCESS
              )
            )
          )
        )
      ),
      AddressBalanceUpdatedEvent(
        address = owner,
        token = Address.ZERO.toString(),
        balance = Some(
          toAmount(ethBalanceTo)
        ),
        block = blockNumber
      ),
      AddressBalanceUpdatedEvent(
        address = owner,
        token = WETH_TOKEN.address,
        balance = Some(
          toAmount(wethBalanceTo)
        ),
        block = blockNumber
      )
    )
  }

  def wethUnWrapConfirmedActivities(
      owner: String,
      blockNumber: Long,
      txHash: String,
      convertAmount: BigInt,
      nonce: Long,
      ethBalanceTo: BigInt,
      wethBalanceTo: BigInt
    ) = {
    Seq(
      TxEvents(
        TxEvents.Events.Activities(
          TxEvents.Activities(
            Seq(
              Activity(
                owner = owner,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_UNWRAP,
                timestamp = timeProvider.getTimeSeconds,
                token = WETH_TOKEN.address,
                detail = Activity.Detail.EtherConversion(
                  Activity.EtherConversion(
                    Some(
                      toAmount(convertAmount)
                    )
                  )
                ),
                nonce = nonce,
                txStatus = TxStatus.TX_STATUS_SUCCESS
              ),
              Activity(
                owner = owner,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_UNWRAP,
                timestamp = timeProvider.getTimeSeconds,
                token = Address.ZERO.toString(),
                detail = Activity.Detail.EtherConversion(
                  Activity.EtherConversion(
                    Some(
                      toAmount(convertAmount)
                    )
                  )
                ),
                nonce = nonce,
                txStatus = TxStatus.TX_STATUS_SUCCESS
              )
            )
          )
        )
      ),
      AddressBalanceUpdatedEvent(
        address = owner,
        token = WETH_TOKEN.address,
        balance = Some(
          toAmount(wethBalanceTo)
        ),
        block = blockNumber
      ),
      AddressBalanceUpdatedEvent(
        address = owner,
        token = Address.ZERO.toString(),
        balance = Some(
          toAmount(ethBalanceTo)
        ),
        block = blockNumber
      )
    )
  }

  def ethTransferFailedActivities(
      from: String,
      to: String,
      blockNumber: Long,
      txHash: String,
      transferAmount: BigInt,
      nonce: Long
    ) = {
    Seq(
      TxEvents(
        TxEvents.Events.Activities(
          TxEvents.Activities(
            Seq(
              Activity(
                owner = from,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_TRANSFER_OUT,
                timestamp = timeProvider.getTimeSeconds,
                token = Address.ZERO.toString(),
                detail = Activity.Detail.EtherTransfer(
                  Activity.EtherTransfer(
                    from,
                    Some(
                      toAmount(transferAmount)
                    )
                  )
                ),
                nonce = nonce,
                txStatus = TxStatus.TX_STATUS_FAILED
              ),
              Activity(
                owner = to,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_TRANSFER_IN,
                timestamp = timeProvider.getTimeSeconds,
                token = Address.ZERO.toString(),
                detail = Activity.Detail.EtherTransfer(
                  Activity.EtherTransfer(
                    to,
                    Some(
                      toAmount(transferAmount)
                    )
                  )
                ),
                nonce = nonce,
                txStatus = TxStatus.TX_STATUS_FAILED
              )
            )
          )
        )
      )
    )
  }

  def wethWrapFailedActivities(
      owner: String,
      blockNumber: Long,
      txHash: String,
      convertAmount: BigInt,
      nonce: Long
    ) = {
    Seq(
      TxEvents(
        TxEvents.Events.Activities(
          TxEvents.Activities(
            Seq(
              Activity(
                owner = owner,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_WRAP,
                timestamp = timeProvider.getTimeSeconds,
                token = Address.ZERO.toString(),
                detail = Activity.Detail.EtherConversion(
                  Activity.EtherConversion(
                    Some(
                      toAmount(convertAmount)
                    )
                  )
                ),
                nonce = nonce,
                txStatus = TxStatus.TX_STATUS_FAILED
              ),
              Activity(
                owner = owner,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_WRAP,
                timestamp = timeProvider.getTimeSeconds,
                token = WETH_TOKEN.address,
                detail = Activity.Detail.EtherConversion(
                  Activity.EtherConversion(
                    Some(
                      toAmount(convertAmount)
                    )
                  )
                ),
                nonce = nonce,
                txStatus = TxStatus.TX_STATUS_FAILED
              )
            )
          )
        )
      )
    )
  }

  def wethUnwrapFailedActivities(
      owner: String,
      blockNumber: Long,
      txHash: String,
      convertAmount: BigInt,
      nonce: Long
    ) = {
    Seq(
      TxEvents(
        TxEvents.Events.Activities(
          TxEvents.Activities(
            Seq(
              Activity(
                owner = owner,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_UNWRAP,
                timestamp = timeProvider.getTimeSeconds,
                token = WETH_TOKEN.address,
                detail = Activity.Detail.EtherConversion(
                  Activity.EtherConversion(
                    Some(
                      toAmount(convertAmount)
                    )
                  )
                ),
                nonce = nonce,
                txStatus = TxStatus.TX_STATUS_FAILED
              ),
              Activity(
                owner = owner,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.ETHER_UNWRAP,
                timestamp = timeProvider.getTimeSeconds,
                token = Address.ZERO.toString(),
                detail = Activity.Detail.EtherConversion(
                  Activity.EtherConversion(
                    Some(
                      toAmount(convertAmount)
                    )
                  )
                ),
                nonce = nonce,
                txStatus = TxStatus.TX_STATUS_FAILED
              )
            )
          )
        )
      )
    )
  }

  def tokenTransferFailedActivities(
      from: String,
      to: String,
      blockNumber: Long,
      txHash: String,
      tokenAddress: String,
      transferAmount: BigInt,
      nonce: Long
    ) = {
    Seq(
      TxEvents(
        TxEvents.Events.Activities(
          TxEvents.Activities(
            Seq(
              Activity(
                owner = from,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.TOKEN_TRANSFER_OUT,
                timestamp = timeProvider.getTimeSeconds,
                token = tokenAddress,
                detail = Activity.Detail.TokenTransfer(
                  Activity.TokenTransfer(
                    from,
                    tokenAddress,
                    Some(
                      toAmount(transferAmount)
                    )
                  )
                ),
                nonce = nonce,
                txStatus = TxStatus.TX_STATUS_FAILED
              ),
              Activity(
                owner = to,
                block = blockNumber,
                txHash = txHash,
                activityType = Activity.ActivityType.TOKEN_TRANSFER_IN,
                timestamp = timeProvider.getTimeSeconds,
                token = tokenAddress,
                detail = Activity.Detail.TokenTransfer(
                  Activity.TokenTransfer(
                    to,
                    tokenAddress,
                    Some(
                      toAmount(transferAmount)
                    )
                  )
                ),
                nonce = nonce,
                txStatus = TxStatus.TX_STATUS_FAILED
              )
            )
          )
        )
      )
    )
  }
}
