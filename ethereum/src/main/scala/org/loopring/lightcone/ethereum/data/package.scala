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

import org.web3j.utils.Numeric

// todo lib里面是否需要定义amount,address,hash等数据结构,与core项目的amount等该如何调用

package object data {

  case class Transfer(sender: String, receiver: String, amount: BigInt)
  case class Approve(owner: String, spender: String, amount: BigInt)

  case class Deposit(owner: String, amount: BigInt)
  case class Withdrawal(owner: String, amount: BigInt)

  implicit def bytes2BigInt(bytes: Array[Byte]): BigInt = Numeric.toBigInt(bytes)
  implicit def hexString2BigInt(hex: String): BigInt = Numeric.toBigInt(hex)

  implicit class RichString(src: String) {

    def eqCaseInsensitive(that: String): Boolean = src.toLowerCase == that.toLowerCase

    def neqCaseInsensitive(that: String): Boolean = src.toLowerCase != that.toLowerCase

  }
}
