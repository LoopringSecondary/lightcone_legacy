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

package org.loopring.lightcone.core.cache

import com.google.inject._
import com.google.inject.name.Named
import org.loopring.lightcone.core.cache.redishash.{ RedisHashGetSerializer, RedisHashSetSerializer }
import org.loopring.lightcone.lib.etypes._
import org.loopring.lightcone.proto.balance._
import org.loopring.lightcone.proto.cache.CacheBalanceInfo
import redis._

import scala.concurrent.ExecutionContext

/** 使用hash保存余额以及授权
 *
 *  余额：HSET address_${address} b_${token} ${amount}
 *  HSET address_0x8d8812b72d1e4ffcec158d25f56748b7d67c1e78 b_0xef68e7c694f40c8202821edf525de3782458639f 10000000
 *
 *  授权: HSET address_${address} a_${delegate}_${token} ${amount}
 *  HSET address_0x8d8812b72d1e4ffcec158d25f56748b7d67c1e78 a_0x17233e07c67d086464fd408148c3abb56245fa64_0xef68e7c694f40c8202821edf525de3782458639f 100000
 */

case class BalanceField(token: String)
case class AllowanceField(delegate: String, token: String)
case class BalanceAndAllowanceField(valueType: String, delegate: String, token: String)

object BalanceRedisCache {
  val delimeter = "_"
  val keyPrefix = "address" + delimeter
  val allowanceField = "a"
  val allowanceFieldPrefix = allowanceField + delimeter
  val balanceField = "b"
  val balanceFieldPrefix = balanceField + delimeter

  //定义serializer
  implicit val balanceSerializer = new RedisHashGetSerializer[GetBalancesReq, BalanceField, GetBalancesResp] {
    override def cacheKey(req: GetBalancesReq): String = keyPrefix + req.address.toLowerCase()

    override def encodeCacheFields(req: GetBalancesReq): Seq[String] = req.tokens.map(t ⇒ balanceFieldPrefix + t.toLowerCase())

    override def decodeCacheField(field: String): BalanceField = BalanceField(field.split(delimeter)(1))

    override def genResp(req: GetBalancesReq, cacheFields: Seq[BalanceField], valueData: Seq[Option[Array[Byte]]]): GetBalancesResp = {
      GetBalancesResp(
        address = req.address,
        balances = cacheFields.zipWithIndex.map { field ⇒
          {
            val token = field._1.token
            TokenAmount(token = token, amount = valueData(field._2) match {
              case None        ⇒ "0"
              case Some(value) ⇒ value.asBigInt().toString()
            })
          }
        }
      )
    }
  }

  implicit val allowanceSerializer = new RedisHashGetSerializer[GetAllowancesReq, AllowanceField, GetAllowancesResp] {
    override def cacheKey(req: GetAllowancesReq): String = keyPrefix + req.address.toLowerCase()

    override def encodeCacheFields(req: GetAllowancesReq): Seq[String] = req.delegates.flatMap { delegate ⇒
      req.tokens.map(t ⇒ allowanceFieldPrefix + delegate.toLowerCase() + delimeter + t.toLowerCase())
    }

    override def decodeCacheField(field: String): AllowanceField = {
      val splitData = field.split(delimeter)
      AllowanceField(splitData(1), splitData(2))
    }

    override def genResp(req: GetAllowancesReq, cacheFields: Seq[AllowanceField], valueData: Seq[Option[Array[Byte]]]): GetAllowancesResp = {
      var allowances = cacheFields
        .zipWithIndex
        .map { field ⇒
          val amount = valueData(field._2) match {
            case None        ⇒ "0"
            case Some(value) ⇒ value.asBigInt().toString()
          }
          (field._1.delegate, field._1.token, amount)
        }
        .groupBy(_._1)
        .map { field ⇒
          val infoSeq = field._2
          Allowance(
            delegate = field._1,
            tokenAmounts = infoSeq.map(info ⇒ TokenAmount(token = info._2, amount = info._3))
          )
        }.toSeq
      GetAllowancesResp(
        address = req.address,
        allowances = allowances
      )
    }
  }

  implicit val balanceAndAllowanceSerializer = new RedisHashGetSerializer[GetBalanceAndAllowanceReq, BalanceAndAllowanceField, GetBalanceAndAllowanceResp] {
    override def cacheKey(req: GetBalanceAndAllowanceReq): String = keyPrefix + req.address.toLowerCase()

    override def encodeCacheFields(req: GetBalanceAndAllowanceReq): Seq[String] = {
      val balanceCacheFields = req.tokens.map(t ⇒ balanceFieldPrefix + t.toLowerCase())
      val allowanceCacheFields = req.delegates.flatMap { delegate ⇒
        req.tokens.map(t ⇒ allowanceFieldPrefix + delegate.toLowerCase() + delimeter + t.toLowerCase())
      }
      balanceCacheFields ++ allowanceCacheFields
    }

    override def decodeCacheField(field: String): BalanceAndAllowanceField = {
      val splitData = field.toString.split(delimeter)
      BalanceAndAllowanceField(splitData(0), splitData(1), splitData(2))
    }

    override def genResp(req: GetBalanceAndAllowanceReq, cacheFields: Seq[BalanceAndAllowanceField], valueData: Seq[Option[Array[Byte]]]): GetBalanceAndAllowanceResp = {
      val dataGroupByType = cacheFields
        .zipWithIndex
        .map { field ⇒
          val amount = valueData(field._2) match {
            case None        ⇒ "0"
            case Some(value) ⇒ value.asBigInt().toString()
          }
          (field._1, amount)
        }
        .groupBy(_._1.valueType)
      val balancesSeq = dataGroupByType.getOrElse(balanceField, Seq())
      val balances = balancesSeq.map { data ⇒
        TokenAmount(token = data._1.token, amount = data._2)
      }
      val allowancesSeq = dataGroupByType.getOrElse(allowanceField, Seq())
      val allowances = allowancesSeq
        .groupBy(_._1.delegate)
        .map { data ⇒
          val infoSeq = data._2
          Allowance(
            delegate = data._1,
            tokenAmounts = infoSeq.map(info ⇒ TokenAmount(token = info._1.token, amount = info._2))
          )
        }.toSeq

      GetBalanceAndAllowanceResp(
        address = req.address,
        allowances = allowances,
        balances = balances
      )
    }
  }

  implicit val balanceSetSerializer = new RedisHashSetSerializer[CacheBalanceInfo] {
    override def cacheKey(req: CacheBalanceInfo): String = keyPrefix + req.address.toLowerCase()

    override def genKeyValues(req: CacheBalanceInfo): Map[String, Array[Byte]] = {
      val balanceMap = req.balances.map { balance ⇒
        (balanceFieldPrefix + balance.token.toLowerCase(), balance.amount.getBytes)
      }.toMap
      val allowanceMap = req.allowances.flatMap { allowance ⇒
        allowance.tokenAmounts.map { tokenAmount ⇒
          val field = allowanceFieldPrefix + allowance.delegate.toLowerCase() + delimeter + tokenAmount.token.toLowerCase()
          (field, tokenAmount.amount.getBytes)
        }
      }.toMap
      balanceMap ++ allowanceMap
    }
  }
}

final class BalanceRedisCache @Inject() (
    val redis: RedisCluster
)(
    implicit
    @Named("system-dispatcher") val ec: ExecutionContext
)
  extends BalanceCache {
  import BalanceRedisCache._

  override def getBalances(req: GetBalancesReq) = hmget[GetBalancesReq, BalanceField, GetBalancesResp](req)
  override def getAllowances(req: GetAllowancesReq) = hmget[GetAllowancesReq, AllowanceField, GetAllowancesResp](req)
  override def getBalanceAndAllowances(req: GetBalanceAndAllowanceReq) = hmget[GetBalanceAndAllowanceReq, BalanceAndAllowanceField, GetBalanceAndAllowanceResp](req)
  override def addCache(cacheInfo: CacheBalanceInfo) = hmset[CacheBalanceInfo](cacheInfo)
}
