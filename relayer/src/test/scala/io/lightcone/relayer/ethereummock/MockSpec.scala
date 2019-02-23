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

import akka.actor.{ActorSystem, Props}
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import akka.util.Timeout
import io.lightcone.lib.SystemTimeProvider
import io.lightcone.relayer.data.{AccountBalance, GetAccount}
import org.scalamock.scalatest.MockFactory
import org.scalatest.WordSpecLike

import scala.concurrent.Await
import scala.concurrent.duration._

class MockSpec
    extends TestKit(ActorSystem("Lightcone"))
    with WordSpecLike
    with MockFactory {
  implicit val timeout = Timeout(5 second)
  implicit val ec = system.dispatcher

  implicit val config = system.settings.config

  implicit val materializer = ActorMaterializer()(system)

  implicit val timeProvider = new SystemTimeProvider()

  "set expect in EthereumQueryDataProvider" must {
    "can be response in MockEthereumQueryActor" in {
      implicit val dataProvider = mock[EthereumQueryDataProvider]

      val ethereumQueryActor =
        system.actorOf(Props(new MockEthereumQueryActor()))
      val req1 = GetAccount.Req("0xaaa")
      (dataProvider.getAccount _)
        .expects(req1)
        .returns(
          GetAccount.Res(Some(AccountBalance(address = "0xbbb", nonce = 190)))
        )
        .anyNumberOfTimes()
      val res1 = Await.result(
        (ethereumQueryActor ? req1).mapTo[GetAccount.Res],
        timeout.duration
      )

      info(s"${res1}")
      val res2 = Await.result(
        (ethereumQueryActor ? GetAccount.Req("0xbadfa")).mapTo[GetAccount.Res],
        timeout.duration
      )
      info(s"${res2}")
    }
  }
}
