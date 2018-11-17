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

package org.loopring.lightcone.actors

import com.google.protobuf.ByteString

import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.core.MarketId
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import org.loopring.lightcone.core.{ MarketId, ExpectedFill ⇒ CExpectedFill, Order ⇒ COrder, OrderState ⇒ COrderState, OrderStatus ⇒ COrderStatus, Ring ⇒ CRing }

package object actor {
  type Amount = BigInt
  type Address = String
  type ID = String
  type RingID = Array[Byte]

  implicit def byteArray2ByteString(bytes: Array[Byte]) = ByteString.copyFrom(bytes)
  implicit def byteString2ByteArray(bs: ByteString) = bs.toByteArray

  implicit def byteString2BigInt(bs: ByteString): BigInt = BigInt(bs.toByteArray)
  implicit def bigIntToByteString(bi: BigInt): ByteString = bi.toByteArray

  implicit def mapOfStringToBigInt2mapOfStringToByteString(
    m: Map[String, BigInt]
  ): Map[String, ByteString] = m.map {
    case (k, v) ⇒ k -> bigIntToByteString(v)
  }

  implicit def mapOfStringToByteString2MapOfStringToBigInt(
    m: Map[String, ByteString]
  ): Map[String, BigInt] = m.map {
    case (k, v) ⇒ k -> byteString2BigInt(v)
  }

  implicit def tokensToMarketHash(tokenS: Address, tokenB: Address): String = {
    val market = BigInt(Hash.sha3(tokenS.getBytes)) ^ BigInt(Hash.sha3(tokenB.getBytes()))
    Numeric.toHexString(market.toByteArray)
  }

  implicit class RichOrderStatus(status: OrderStatus) {
    def toPojo(): COrderStatus.Value = status match {
      case OrderStatus.NEW ⇒ COrderStatus.NEW
      case OrderStatus.PENDING ⇒ COrderStatus.PENDING
      case OrderStatus.EXPIRED ⇒ COrderStatus.EXPIRED
      case OrderStatus.CANCELLED_BY_USER ⇒ COrderStatus.CANCELLED_BY_USER
      case OrderStatus.COMPLETELY_FILLED ⇒ COrderStatus.COMPLETELY_FILLED
      case OrderStatus.CANCELLED_LOW_BALANCE ⇒ COrderStatus.CANCELLED_LOW_BALANCE
      case OrderStatus.CANCELLED_LOW_FEE_BALANCE ⇒ COrderStatus.CANCELLED_LOW_FEE_BALANCE
      case OrderStatus.CANCELLED_TOO_MANY_ORDERS ⇒ COrderStatus.CANCELLED_TOO_MANY_ORDERS
      case OrderStatus.CANCELLED_TOO_MANY_FAILED_SETTLEMENTS ⇒ COrderStatus.CANCELLED_TOO_MANY_FAILED_SETTLEMENTS
      case v ⇒ throw new IllegalArgumentException(s"$v not suppported")
    }
  }

  implicit class RichCOrderStatus(status: COrderStatus.Value) {
    def toProto(): OrderStatus = status match {
      case COrderStatus.NEW ⇒ OrderStatus.NEW
      case COrderStatus.PENDING ⇒ OrderStatus.PENDING
      case COrderStatus.EXPIRED ⇒ OrderStatus.EXPIRED
      case COrderStatus.COMPLETELY_FILLED ⇒ OrderStatus.COMPLETELY_FILLED
      case COrderStatus.CANCELLED_BY_USER ⇒ OrderStatus.CANCELLED_BY_USER
      case COrderStatus.CANCELLED_LOW_BALANCE ⇒ OrderStatus.CANCELLED_LOW_BALANCE
      case COrderStatus.CANCELLED_LOW_FEE_BALANCE ⇒ OrderStatus.CANCELLED_LOW_FEE_BALANCE
      case COrderStatus.CANCELLED_TOO_MANY_ORDERS ⇒ OrderStatus.CANCELLED_TOO_MANY_ORDERS
      case COrderStatus.CANCELLED_TOO_MANY_FAILED_SETTLEMENTS ⇒ OrderStatus.CANCELLED_TOO_MANY_FAILED_SETTLEMENTS
      case v ⇒ throw new IllegalArgumentException(s"$v not suppported")
    }
  }

  implicit class RichOrderState(state: OrderState) {
    def toPojo(): COrderState = COrderState(
      state.amountS,
      state.amountB,
      state.amountFee
    )
  }

  implicit class RichCOrderState(state: COrderState) {
    def toProto(): OrderState = OrderState(
      state.amountS,
      state.amountB,
      state.amountFee
    )
  }

  implicit class RichOrder(order: Order) {
    def toPojo(): COrder = COrder(
      order.id,
      order.tokenS,
      order.tokenB,
      order.tokenFee,
      order.amountS,
      order.amountB,
      order.amountFee,
      order.createdAt,
      order.status.toPojo,
      order.walletSplitPercentage,
      order.outstanding.map(_.toPojo),
      order.reserved.map(_.toPojo),
      order.actual.map(_.toPojo),
      order.matchable.map(_.toPojo)
    )
  }

  implicit class RichCOrder(order: COrder) {
    def toProto(): Order = Order(
      order.id,
      order.tokenS,
      order.tokenB,
      order.tokenFee,
      order.amountS,
      order.amountB,
      order.amountFee,
      order.createdAt,
      order.status.toProto,
      order.walletSplitPercentage,
      order._outstanding.map(_.toProto),
      order._reserved.map(_.toProto),
      order._actual.map(_.toProto),
      order._matchable.map(_.toProto)
    )
  }

  implicit class RichExpectedFill(ef: ExpectedFill) {
    def toPojo(): CExpectedFill = CExpectedFill(
      ef.order.map(_.toPojo).getOrElse(null),
      ef.pending.map(_.toPojo).getOrElse(null),
      ef.amountMargin
    )
  }

  implicit class RichCExpectedFill(ef: CExpectedFill) {
    def toProto(): ExpectedFill = ExpectedFill(
      Some(ef.order.toProto),
      Some(ef.pending.toProto),
      ef.amountMargin
    )
  }

  implicit class RichCRing(ring: CRing) {
    def toProto(): Ring = Ring(
      Some(ring.maker.toProto),
      Some(ring.taker.toProto)
    )
  }

  implicit class RichRing(ring: Ring) {
    def toPojo(): CRing = CRing(
      ring.getMaker.toPojo,
      ring.getTaker.toPojo
    )
  }

  implicit class RichMarketId(src: MarketId) {

    def ID: String = tokensToMarketHash(src.primary, src.secondary)
  }
}
