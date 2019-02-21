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

import io.lightcone.relayer.data._
import io.lightcone.relayer.ethereum._

object DataPool {
  var results = Map.empty[String, AnyRef]

  def getResult(req: Any) = results.get(getRequestHash(req))

  def setResult(
      req1: Any,
      res: AnyRef
    ) = req1 match {
    case req: GetBalanceAndAllowances.Req =>
      results = results + (getRequestHash(req) -> res)

    case req =>
      results = results + (getRequestHash(req) -> res)
  }

  def dispatchEvent(evt: AnyRef)(implicit dispatcher: EventDispatcher) = {
    dispatcher.dispatch(evt)
  }

  def getRequestHash(req1: Any): String = req1 match {
    case req: NodeBlockHeight =>
      s"req_${req.nodeName}"
    case req: JsonRpc.Request =>
      s"rpc_req_${req.json}"
    case r: SendRawTransaction.Req =>
      s"rawtx_${r.data}"

    case req: GetBlockNumber.Req =>
      s"blockNumber"
    case req: EthGetBalance.Req =>
      s"eth_balance_${req.address}_${req.tag}"

    case req: GetTransactionByHash.Req =>
      s"tx_by_hash_${req.hash}"

    case req: GetTransactionReceipt.Req =>
      s"tx_receipt_${req.hash}"

    case req: GetBlockWithTxHashByNumber.Req =>
      s"block_with_txhash_by_number_${req.blockNumber}"

    case req: GetBlockWithTxObjectByNumber.Req =>
      s"block_with_object_by_number_${req.blockNumber}"

    case req: GetBlockWithTxHashByHash.Req =>
      s"block_with_txhash_by_hash_${req.blockHash}"

    case req: GetBlockWithTxObjectByHash.Req =>
      s"block_with_object_by_hash_${req.blockHash}"

    case req: TraceTransaction.Req =>
      s"trace_tx_${req.txhash}"

    case req: GetEstimatedGas.Req =>
      s"eth_gas_${req.to}_${req.data}"

    case req: GetNonce.Req =>
      s"nonce_${req.owner}_${req.tag}"

    case req: GetBlockTransactionCount.Req =>
      s"block_txcount_by_hash_${req.blockHash}"

    case EthCall.Req(_, Some(param), _) =>
      s"call_${param.from}_${param.to}_${param.data}_${param.value}"

    case req: GetUncle.Req =>
      s"uncle_${req.blockNum}_${req.index}"

    //batch的请求不需要再次设置
    case batchR: BatchCallContracts.Req =>
      throw new Exception("not support")

    case batchR: BatchGetTransactionReceipts.Req =>
      throw new Exception("not support")

    case batchR: BatchGetTransactions.Req =>
      throw new Exception("not support")

    case batchR: BatchGetUncle.Req =>
      throw new Exception("not support")

    case batchR: BatchGetEthBalance.Req =>
      throw new Exception("not support")

    case req @ Notify("init", _) =>
      s"notify_init"

  }
}
