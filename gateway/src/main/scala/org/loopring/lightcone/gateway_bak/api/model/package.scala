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

package org.loopring.lightcone.gateway_bak.api

package object model {

  case class TokenSpendables(
      symbol: String,
      balance: String,
      allowance: String)

  // QUESTION(Doan): 是不是要加一个token的列表？
  case class TokenSpendablesReq(
      owner: String,
      delegateAddress: String)

  case class TokenSpendablesResp(
      owner: String,
      delegateAddress: String,
      tokens: Seq[TokenSpendables])
}
