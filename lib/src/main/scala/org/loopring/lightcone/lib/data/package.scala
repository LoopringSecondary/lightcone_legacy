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

package org.loopring.lightcone.lib

import com.google.protobuf.ByteString

package object data {

  // implicit def byteString2BigInt(bytes: ByteString): BigInt = {
  //   if (bytes.size() > 0) BigInt(bytes.toByteArray)
  //   else BigInt(0)
  // }

  // implicit def bigInt2ByteString(b: BigInt): ByteString =
  //   ByteString.copyFrom(b.toByteArray)

  // implicit def byteArray2ByteString(bytes: Array[Byte]) =
  //   ByteString.copyFrom(bytes)

}
