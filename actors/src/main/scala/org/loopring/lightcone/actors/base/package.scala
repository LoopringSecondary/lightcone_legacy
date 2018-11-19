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

package org.loopring.lightcone.actors.base

import com.google.protobuf.ByteString
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.proto.actors.XOrder

package object data {

  implicit def xOrderToOrder(xorder: XOrder): Order = ???

  implicit def byteArray2ByteString(bytes: Array[Byte]) = ByteString.copyFrom(bytes)
}
