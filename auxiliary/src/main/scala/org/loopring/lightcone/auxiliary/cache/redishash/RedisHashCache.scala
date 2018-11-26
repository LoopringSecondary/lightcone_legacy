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

package org.loopring.lightcone.core.cache.redishash

import redis.RedisCluster

import scala.concurrent.{ ExecutionContext, Future }

/*

  Copyright 2017 Loopring Project Ltd (Loopring Foundation).

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

*/

trait RedisHashCache {
  val redis: RedisCluster
  implicit val ec: ExecutionContext

  def hmget[T, F, R](req: T)(implicit s: RedisHashGetSerializer[T, F, R]): Future[R] = for {
    cacheFields ← Future.successful(s.encodeCacheFields(req))
    valueData ← redis.hmget[Array[Byte]](s.cacheKey(req), cacheFields: _*)
  } yield {
    val fields = cacheFields.map(s.decodeCacheField)
    s.genResp(req, fields, valueData)
  }

  def hmset[T](req: T)(implicit s: RedisHashSetSerializer[T]) = {
    redis.hmset[Array[Byte]](s.cacheKey(req), s.genKeyValues(req))
  }

}
