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
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.proto.actors._
import com.google.protobuf.ByteString

// TODO(hongyu): implement this
package object data {

  case class BalanceAndAllowance(balance: BigInt, allowance: BigInt)

  ///////////

  implicit def byteString2BigInt(bytes: ByteString): BigInt = ???
  implicit def bigInt2ByteString(b: BigInt): ByteString = ???

  implicit def xBalanceAndAlowance2BalanceAndAlowance(ba: XBalanceAndAllowance): BalanceAndAllowance = ???
  implicit def balanceAndAlowance2XBalanceAndAlowance(ba: BalanceAndAllowance): XBalanceAndAllowance = ???

  implicit def xOrder2Order(xorder: XOrder): Order = ???
  implicit def order2XOrder(order: Order): XOrder = ???

  implicit def byteArray2ByteString(bytes: Array[Byte]) = ByteString.copyFrom(bytes)
}
