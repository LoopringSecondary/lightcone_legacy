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

import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.proto.actors._

class EthReqManger(erc20Abi: ERC20ABI) {

  def packBatchBalanceCallReq(owner: Address, tokens: Seq[Address], tag: String = "latest"): XBatchContractCallReq = {
    val balanceCallReqs = tokens.map(token ⇒ packBalanceCallReq(owner, token, tag))
    XBatchContractCallReq(balanceCallReqs)
  }

  def packBatchAllowanceCallReq(owner: Address, tokens: Seq[Address], _spender: Address, tag: String = "latest"): XBatchContractCallReq = {
    val allowanceCallReqs = tokens.map(token ⇒ packAllowanceCallReq(owner, token, _spender, tag))
    XBatchContractCallReq(allowanceCallReqs)
  }

  def packBalanceCallReq(owner: Address, token: Address, tag: String = "latest"): XEthCallReq = {
    val data = erc20Abi.balanceOf.pack(BalanceOfFunction.Parms(_owner = owner.toString))
    val param = XTransactionParam(to = token.toString, data = data)
    XEthCallReq(Some(param), tag)
  }

  def packAllowanceCallReq(owner: Address, token: Address, _spender: Address, tag: String = "latest"): XEthCallReq = {
    val data = erc20Abi.allowance.pack(
      AllowanceFunction.Parms(_spender = _spender.toString, _owner = owner.toString)
    )
    val param = XTransactionParam(to = token.toString, data = data)
    XEthCallReq(Some(param), tag)
  }
}

object EthReqManger {
  def apply(erc20Abi: ERC20ABI): EthReqManger = new EthReqManger(erc20Abi)

  def apply(): EthReqManger = new EthReqManger(ERC20ABI())
}
