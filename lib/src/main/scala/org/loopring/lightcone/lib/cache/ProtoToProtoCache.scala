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

package org.loopring.lightcone.lib.cache

import org.loopring.lightcone.lib.cache.serializer._
import scala.concurrent._

/*
// Example usage
private object ReaderExample {
  class ExampledReader extends Reader[ExampleReq, ExampleResp] {
    def read(req: ExampleReq) = ??? // read from ethereum
    def read(reqs: Seq[ExampleReq]) = ??? // read from ethereum
  }

  implicit val cache: ByteArrayRedisCache = ???
  import scala.concurrent.ExecutionContext.Implicits.global

  val reader = new ProtoToProtoCachedReader(
    new ExampleReader,
    (req: ExampleReq) => req.id.getBytes)

  reader.read(Example1Req("123"))
}
*/

final class ProtoToProtoCache[R <: scalapb.GeneratedMessage with scalapb.Message[R], V <: scalapb.GeneratedMessage with scalapb.Message[V]](
    val underlying: ByteArrayCache,
    val serializer: ProtoSerializer[V],
    val genKey: R ⇒ Array[Byte]
)(implicit val ex: ExecutionContext)
  extends ProtoCache[R, V] {
  def keyToBytes(key: R) = genKey(key)
}
