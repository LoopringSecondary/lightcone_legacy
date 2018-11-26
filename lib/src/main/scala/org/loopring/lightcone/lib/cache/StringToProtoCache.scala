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
  class ExampleReader extends Reader[String, ExampleResp] {
    def read(req: String) = ??? // read from ethereum
    def read(reqs: Seq[String]) = ??? // read from ethereum
  }

  implicit val cache: ByteArrayRedisCache = ???
  import scala.concurrent.ExecutionContext.Implicits.global

  val reader = new StringToProtoCachedReader(new ExampleReader)
  reader.read(Seq("1", "2"))
}
*/

final class StringToProtoCache[V <: scalapb.GeneratedMessage with scalapb.Message[V]](
    val underlying: ByteArrayCache,
    val serializer: ProtoSerializer[V]
)(implicit val ex: ExecutionContext)
  extends ProtoCache[String, V] {
  def keyToBytes(str: String) = str.getBytes
}
