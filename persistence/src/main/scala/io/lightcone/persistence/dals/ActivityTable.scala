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
import io.lightcone.persistence.Activity.ActivityType
import io.lightcone.persistence.Activity.ActivityType._
import io.lightcone.persistence._
import io.lightcone.persistence.base._
import slick.jdbc.MySQLProfile.api._

class ActivityTable(tag: Tag) extends BaseTable[Activity](tag, "T_ACTIVITIES") {

  implicit val activityTypeCxolumnType = enumColumnType(ActivityType)

  def id = ""
  def owner = columnAddress("owner")
  def token = columnAddress("token")
  def block = column[Long]("order_hash")
  def txHash = columnHash("ring_hash")
  def activityType = column[ActivityType]("activity_type")
  def timestamp = column[Long]("timestamp")
  def fiatValue = column[Double]("fiat_value")
  def detail = column[Array[Byte]]("detail")

  def * =
    (
      owner,
      token,
      block,
      txHash,
      activityType,
      timestamp,
      fiatValue,
      detail
    ) <> ({ tuple =>
      val t = tuple._5
      val detailBytes = tuple._8
      val detailValue = t match {
        case ETHER_TRANSFER_OUT | ETHER_TRANSFER_IN =>
          val v = Activity.EtherTransfer.parseFrom(detailBytes)
          Activity.Detail.EtherTransfer(v)
        case ETHER_WRAP | ETHER_UNWRAP =>
          val v = Activity.EtherConversion.parseFrom(detailBytes)
          Activity.Detail.EtherConversion(v)
        case TOKEN_TRANSFER_OUT | TOKEN_TRANSFER_IN =>
          val v = Activity.TokenTransfer.parseFrom(detailBytes)
          Activity.Detail.TokenTransfer(v)
        case TOKEN_AUTH =>
          val v = Activity.TokenAuth.parseFrom(detailBytes)
          Activity.Detail.TokenAuth(v)
        case TRADE_SELL | TRADE_BUY =>
          val v = Activity.Trade.parseFrom(detailBytes)
          Activity.Detail.Trade(v)
        case ORDER_CANCEL =>
          val v = Activity.OrderCancellation.parseFrom(detailBytes)
          Activity.Detail.OrderCancellation(v)
        case ORDER_SUBMIT => // todo:
          val v = Activity.OrderSubmission.parseFrom(detailBytes)
          Activity.Detail.OrderSubmission(v)
        case _ =>
          throw ErrorException(
            ErrorCode.ERR_INVALID_ARGUMENT,
            s"invalid activity_type $t"
          )
      }
      Activity(
        owner = tuple._1,
        token = tuple._2,
        block = tuple._3,
        txHash = tuple._4,
        activityType = t,
        timestamp = tuple._6,
        fiatValue = tuple._7,
        detail = detailValue
      )
    }, { activity: Activity =>
      val detailBytes = activity.activityType match {
        case ETHER_TRANSFER_OUT | ETHER_TRANSFER_IN =>
          activity.detail.etherTransfer.get.toByteArray
        case ETHER_WRAP | ETHER_UNWRAP =>
          activity.detail.etherConversion.get.toByteArray
        case TOKEN_TRANSFER_OUT | TOKEN_TRANSFER_IN =>
          activity.detail.tokenTransfer.get.toByteArray
        case TOKEN_AUTH =>
          activity.detail.tokenAuth.get.toByteArray
        case TRADE_SELL | TRADE_BUY =>
          activity.detail.trade.get.toByteArray
        case ORDER_CANCEL =>
          activity.detail.orderCancellation.get.toByteArray
        case ORDER_SUBMIT =>
          activity.detail.orderSubmission.get.toByteArray
        case _ =>
          throw ErrorException(
            ErrorCode.ERR_INVALID_ARGUMENT,
            s"invalid activity_type ${activity.activityType}"
          )
      }
      Some(
        (
          activity.owner,
          activity.token,
          activity.block,
          activity.txHash,
          activity.activityType,
          activity.timestamp,
          activity.fiatValue,
          detailBytes
        )
      )
    })
}
