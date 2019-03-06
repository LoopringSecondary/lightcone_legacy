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

package io.lightcone.persistence.dals

import io.lightcone.core.{ErrorCode, ErrorException}
import io.lightcone.ethereum.TxStatus
import io.lightcone.ethereum.persistence.Activity.ActivityType
import io.lightcone.ethereum.persistence.Activity.ActivityType._
import io.lightcone.ethereum.persistence.Activity.Detail._
import io.lightcone.ethereum.persistence._
import io.lightcone.persistence.base._
import slick.jdbc.MySQLProfile.api._

class ActivityTable(shardId: String)(tag: Tag)
    extends BaseTable[Activity](tag, s"T_ACTIVITIES_${shardId.toUpperCase}") {

  implicit val activityTypeCxolumnType = enumColumnType(ActivityType)
  implicit val txStatusCxolumnType = enumColumnType(TxStatus)

  def id = ""
  def owner = columnAddress("owner")
  def token = columnAddress("token")
  def block = column[Long]("block")
  def txHash = columnHash("tx_hash")
  def activityType = column[ActivityType]("activity_type")
  def timestamp = column[Long]("timestamp")
  def fiatValue = column[Double]("fiat_value")
  def details = column[Array[Byte]]("details")
  def sequenceId = column[Long]("sequence_id", O.PrimaryKey, O.AutoInc)
  def from = columnAddress("from")
  def nonce = column[Long]("nonce")
  def txStatus = column[TxStatus]("tx_status")

  // indexes
  def idx_owner_token_sequence =
    index(
      "idx_owner_token_sequence",
      (owner, token, sequenceId),
      unique = false
    )

  def idx_txhash = index("idx_txhash", (txHash), unique = false)
  def idx_timestamp = index("idx_timestamp", (timestamp), unique = false)

  def idx_block_sequence =
    index("idx_block_sequence", (block, sequenceId), unique = false)
  def idx_from_block_nonce = index("idx_from_block_nonce", (from, block, nonce))

  def * =
    (
      owner,
      token,
      block,
      txHash,
      activityType,
      timestamp,
      fiatValue,
      details,
      sequenceId,
      from,
      nonce,
      txStatus
    ) <> ({ tuple =>
      Activity(
        owner = tuple._1,
        token = tuple._2,
        block = tuple._3,
        txHash = tuple._4,
        activityType = tuple._5,
        timestamp = tuple._6,
        fiatValue = tuple._7,
        detail = parseToDetail(tuple._5, tuple._8),
        sequenceId = tuple._9,
        from = tuple._10,
        nonce = tuple._11,
        txStatus = tuple._12
      )
    }, { activity: Activity =>
      Some(
        (
          activity.owner,
          activity.token,
          activity.block,
          activity.txHash,
          activity.activityType,
          activity.timestamp,
          activity.fiatValue,
          printToBytes(activity.detail),
          activity.sequenceId,
          activity.from,
          activity.nonce,
          activity.txStatus
        )
      )
    })

  private def parseToDetail(
      activityType: ActivityType,
      detailBytes: Array[Byte]
    ) = {
    activityType match {
      case ETHER_TRANSFER_OUT | ETHER_TRANSFER_IN =>
        Activity.Detail.EtherTransfer(
          Activity.EtherTransfer.parseFrom(detailBytes)
        )
      case ETHER_WRAP | ETHER_UNWRAP =>
        Activity.Detail.EtherConversion(
          Activity.EtherConversion.parseFrom(detailBytes)
        )
      case TOKEN_TRANSFER_OUT | TOKEN_TRANSFER_IN =>
        Activity.Detail.TokenTransfer(
          Activity.TokenTransfer.parseFrom(detailBytes)
        )
      case TOKEN_AUTH =>
        Activity.Detail.TokenAuth(
          Activity.TokenAuth.parseFrom(detailBytes)
        )
      case TRADE_SELL | TRADE_BUY =>
        Activity.Detail.Trade(
          Activity.Trade.parseFrom(detailBytes)
        )
      case ORDER_CANCEL =>
        Activity.Detail.OrderCancellation(
          Activity.OrderCancellation.parseFrom(detailBytes)
        )
      case ORDER_SUBMIT => // TODO:需要确定实现
        Activity.Detail.OrderSubmission(
          Activity.OrderSubmission.parseFrom(detailBytes)
        )
      case _ =>
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          s"invalid activity_type $activityType"
        )
    }
  }

  private def printToBytes(detail: Activity.Detail) = {
    detail match {
      case EtherTransfer(value)     => value.toByteArray
      case TokenTransfer(value)     => value.toByteArray
      case EtherConversion(value)   => value.toByteArray
      case TokenAuth(value)         => value.toByteArray
      case Trade(value)             => value.toByteArray
      case OrderCancellation(value) => value.toByteArray
      case OrderSubmission(value)   => value.toByteArray
      case value =>
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          s"invalid data of Activity.Detail: $value"
        )
    }
  }

}
