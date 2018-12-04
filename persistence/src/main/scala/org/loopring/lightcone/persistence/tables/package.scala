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

package org.loopring.lightcone.persistence

import com.google.protobuf.ByteString
import org.loopring.lightcone.proto.core.XOrderStatus
import slick.jdbc.MySQLProfile.api._

package object tables {

  implicit val StatusTypeMapper = MappedColumnType.base[XOrderStatus, Int](
    s ⇒ s.value,
    s ⇒ XOrderStatus.fromValue(s)
  )

  implicit val ByteStringTypeMapper = MappedColumnType.base[ByteString, String](
    s ⇒ s.toStringUtf8,
    s ⇒ ByteString.copyFrom(s, "utf-8")
  )
}
