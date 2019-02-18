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

package io.lightcone.relayer

package object socketio {

  case class SubscribeBalanceAndAllowance(
      addresses: Seq[String],
      tokens: Seq[String])

  case class SubscribeTransaction(
      addresses: Seq[String],
      statuses: Seq[String],
      types: Seq[String])

  case class TokenBalanceAndAllowance(
      address: String,
      balance: String,
      allowance: String,
      availableBalance: String,
      availableAllowance: String)

  case class BalanceAndAllowanceResponse(
      owner: String,
      balanceAndAllowance: TokenBalanceAndAllowance)

  case class Transaction(
      from: String,
      to: String,
      value: String,
      gasPrice: String,
      gasLimit: String,
      gasUsed: String = "0x0",
      data: String,
      nonce: String,
      hash: String,
      blockNum: String,
      time: String,
      status: String,
      `type`: String)

  case class TransactionResponse(
      owner: String,
      transaction: Transaction)

}
