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

package org.loopring.lightcone.ethereum

import com.google.protobuf.ByteString
import org.loopring.lightcone.ethereum.abi.OrdersCancelledEvent.Result
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.MarketHashProvider.convert2Hex
import org.loopring.lightcone.proto.{OrdersCancelledEvent, _}
import org.web3j.utils.Numeric

import scala.collection.mutable.ListBuffer

package object event {
  val wethAbi = WETHABI()
  val ringSubmitterAbi = RingSubmitterAbi()
  val loopringProtocolAbi = LoopringProtocolAbi()

  implicit def bytes2ByteString(bytes: Array[Byte]): ByteString =
    ByteString.copyFrom(bytes)

}
