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

package org.loopring.lightcone.persistence.order

import org.loopring.lightcone.ethereum.data.{ Order, Ring }
import org.loopring.lightcone.persistence._
import org.scalatest._
import akka.stream.alpakka.slick.scaladsl._
import akka.stream.scaladsl._
import slick.jdbc.GetResult
import akka.actor._
import akka.stream.ActorMaterializer
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.Future
import akka.Done
import org.loopring.lightcone.persistence.dals._
import org.loopring.lightcone.proto.core.XRawOrder

class OrderSpec extends FlatSpec with Matchers {

  "addOrderTest" should "add a order" in {
    info("[sbt persistence/'testOnly *OrderSpec -- -z addOrderTest']")

    import org.loopring.lightcone.persistence.utils.executor.ExecutorService._

    val module = new PersistenceModuleImpl(
      DatabaseConfig.forConfig[JdbcProfile]("db.default"),
      dbio
    )
    module.generateDDL()
    val orderDal = new OrderDalImpl(module)
    val o = XRawOrder(hash = "0x123")
    for {
      id ← orderDal.saveOrder(o)
    } yield {
      println(" id ......: " + id)
    }
    Thread.sleep(10000)
  }
}
