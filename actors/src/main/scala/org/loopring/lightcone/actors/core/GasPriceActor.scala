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

package org.loopring.lightcone.actors.core

import akka.actor._
import akka.util.Timeout
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.proto.actors._

import scala.concurrent.ExecutionContext

object GasPriceActor {
  val name = "gas_price"
}

class GasPriceActor(defaultGasPrice: BigInt = BigInt("10000000000"))(
    implicit
    ec: ExecutionContext,
    timeout: Timeout
)
  extends Actor
  with ActorLogging {
  private var gasPrice = defaultGasPrice

  def receive: Receive = {

    case XSetGasPriceReq(price) ⇒
      sender ! XSetGasPriceRes(gasPrice)
      gasPrice = price

    case req: XGetGasPriceReq ⇒
      sender ! XGetGasPriceRes(gasPrice)
  }

}
