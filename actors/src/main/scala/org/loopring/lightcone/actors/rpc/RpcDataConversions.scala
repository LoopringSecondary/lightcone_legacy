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

package org.loopring.lightcone.actors.rpc
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric
import org.loopring.lightcone.ethereum.data.{formatHex, Address}
import org.loopring.lightcone.proto.GetTrades.Req

object RpcDataConversions {

  implicit def convertSubmitOrderReq(r: RawOrder): SubmitOrder.Req = {
    SubmitOrder.Req(
      rawOrder = Some(r)
    )
  }

  implicit def convertSubmitOrderRes(
      r: SubmitOrder.Res
    ): rpcdata.SubmitOrder.Result =
    rpcdata.SubmitOrder.Result(orderHash = r.order.get.id)

  implicit def convertGetOrdersReq(
      r: rpcdata.GetOrders.Params
    ): GetOrdersForUser.Req = {
    val market = r.market.map { market =>
      GetOrdersForUser.Req.Market(
        market.baseToken,
        market.quoteToken,
        isQueryBothSide = true
      )
    }
    //TODO 前端需要暴露这么多order的状态出去吗？
    val statuses: Seq[OrderStatus] = r.statuses.map { status =>
      OrderStatus.fromValue(Numeric.toBigInt(formatHex(status)).intValue())
    }
    val pageNum = if (r.pageNum == 0) 1 else r.pageNum
    val pageSize = if (r.pageSize == 0) 20 else r.pageSize
    GetOrdersForUser.Req(
      owner = r.owner,
      market = market,
      statuses = statuses,
      sort = SortingType.fromName(r.sort).getOrElse(SortingType.ASC),
      skip = Some(
        Paging(
          skip = (pageNum - 1) * pageSize,
          size = pageSize
        )
      )
    )
  }

  implicit def convertGetTrades(r: rpcdata.GetTrades.Params): GetTrades.Req = {
    val market = r.market.map { market =>
      GetTrades.Req.Market(
        market.baseToken,
        market.quoteToken,
        isQueryBothSide = true
      )
    }
    val pageNum = if (r.pageNum == 0) 1 else r.pageNum
    val pageSize = if (r.pageSize == 0) 20 else r.pageSize
    GetTrades.Req(
      owner = r.owner,
      market = market,
      sort = SortingType.fromName(r.sort).getOrElse(SortingType.ASC),
      skip = Some(
        Paging(
          skip = (pageNum - 1) * pageSize,
          size = pageSize
        )
      )
    )
  }

  implicit def convertGetRingsReq(r: rpcdata.GetRings.Params): GetRings.Req = {
    val pageNum = if (r.pageNum == 0) 1 else r.pageNum
    val pageSize = if (r.pageSize == 0) 20 else r.pageSize

    val ring =
      if (r.ringIndex.nonEmpty)
        Some(
          GetRings.Req.Ring(
            GetRings.Req.Ring.Filter.RingIndex(BigInt(r.ringIndex).longValue())
          )
        )
      else if (r.ringHash.nonEmpty)
        Some(GetRings.Req.Ring(GetRings.Req.Ring.Filter.RingHash(r.ringHash)))
      else None

    GetRings.Req(
      ring = ring,
      sort = SortingType.fromName(r.sort).getOrElse(SortingType.ASC),
      skip = Some(
        Paging(
          skip = (pageNum - 1) * pageSize,
          size = pageSize
        )
      )
    )
  }

  implicit def convertGetTransactionsReq(
      r: rpcdata.GetTransactions.Params
    ): GetTransactionRecords.Req = {
    val pageNum = if (r.pageNum == 0) 1 else r.pageNum
    val pageSize = if (r.pageSize == 0) 20 else r.pageSize

    val queryType = if (r.`type`.nonEmpty) {
      val recordType = TransactionRecord.RecordType.fromName(r.`type`)
      recordType.map(r => GetTransactionRecords.QueryType(r))
    } else None

    GetTransactionRecords.Req(
      owner = Address.normalize(r.owner),
      queryType = queryType,
      sort = SortingType.fromName(r.sort).getOrElse(SortingType.ASC),
      paging = Some(
        CursorPaging(
          cursor = pageNum, // TODO  cursor代表什么含义
          size = pageSize
        )
      )
    )
  }

  implicit def convertGetTokensRes(
      r: GetMetadatas.Res
    ): rpcdata.GetTokens.Result =
    rpcdata.GetTokens.Result(r.tokens)

  implicit def convertGetMarketsRes(
      r: GetMetadatas.Res
    ): rpcdata.GetMarkets.Result =
    rpcdata.GetMarkets.Result(r.markets)

}
