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

package io.lightcone.relayer

import io.lightcone.core._
import akka.actor.ActorRef
import akka.pattern.ask
import scala.concurrent.Await
import scala.concurrent.duration._

trait OrderHelper extends IntegrationConstants {
  implicit val timeout = akka.util.Timeout(10 seconds)

  case class _SomeToken(
      amount: Double,
      tokenAddress: String) {
    def ->(another: _SomeToken) = _OrderRep(this, another, None)
  }

  case class _OrderRep(
      sell: _SomeToken,
      buy: _SomeToken,
      fee: Option[_SomeToken]) {
    def --(fee: _SomeToken) = copy(fee = Some(fee))
  }

  implicit class _RichDoubleAmount(v: Double) {
    def ^(str: String) = _SomeToken(v, str)
    def lrc = _SomeToken(v, LRC)
    def gto = _SomeToken(v, GTO)
    def weth = _SomeToken(v, WETH)
  }

  implicit class _RichStringAddress(owner: String) {

    def >>(or: _OrderRep): RawOrder =
      RawOrder(
        owner = owner,
        tokenS = or.sell.tokenAddress,
        tokenB = or.buy.tokenAddress
      )
  }

  implicit class _RichActorRef(actor: ActorRef) {

    // def ??(msg: Any): Any = {
    //   Await.result(actor ? msg, 10 seconds)
    // }

    def ??[T](msg: Any): T = {
      Await.result(actor ? msg, 10 seconds).asInstanceOf[T]
    }
  }

}
