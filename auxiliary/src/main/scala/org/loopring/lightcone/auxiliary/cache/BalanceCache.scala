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

import org.loopring.lightcone.core.cache.redishash.RedisHashCache
import org.loopring.lightcone.proto.balance._
import org.loopring.lightcone.proto.cache.CacheBalanceInfo
import redis.RedisCluster

import scala.concurrent.{ ExecutionContext, Future }

trait BalanceCache extends RedisHashCache {
  val redis: RedisCluster
  implicit val ec: ExecutionContext
  def getBalances(req: GetBalancesReq): Future[GetBalancesResp]
  def getAllowances(req: GetAllowancesReq): Future[GetAllowancesResp]
  def getBalanceAndAllowances(req: GetBalanceAndAllowanceReq): Future[GetBalanceAndAllowanceResp]
  def addCache(cacheInfo: CacheBalanceInfo): Future[Boolean]
}
