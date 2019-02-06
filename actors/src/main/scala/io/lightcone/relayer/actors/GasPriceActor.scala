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

package io.lightcone.relayer.actors

import akka.actor._
import akka.cluster.sharding._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.lib._
import io.lightcone.relayer.base._
import io.lightcone.core._
import io.lightcone.core.ErrorCode._
import io.lightcone.core.OrderStatus._
import io.lightcone.proto._
import io.lightcone.relayer.base.safefuture._
import scala.concurrent._

// Owner: Yadong
object GasPriceActor extends DeployedAsShardedEvenly {
  val name = "gas_price"

  def start(
    implicit system: ActorSystem,
    config: Config,
    ec: ExecutionContext,
    timeProvider: TimeProvider,
    timeout: Timeout,
    actors: Lookup[ActorRef],
    deployActorsIgnoringRoles: Boolean): ActorRef = {
    startSharding(Props(new GasPriceActor()))
  }
}

class GasPriceActor(
  implicit val config: Config,
  val ec: ExecutionContext,
  val timeProvider: TimeProvider,
  val timeout: Timeout,
  val actors: Lookup[ActorRef])
  extends InitializationRetryActor {

  val selfConfig = config.getConfig(GasPriceActor.name)

  private var gasPrice = BigInt(selfConfig.getString("default"))
  private val blockSize = selfConfig.getInt("block-size")
  private val excludePercent = selfConfig.getInt("exclude-percent")

  var blocks: Seq[BlockGasPrices] = Seq.empty

  def ready: Receive = {

    case SetGasPrice.Req(price) =>
      sender ! SetGasPrice.Res(gasPrice)
      gasPrice = price

    case req: GetGasPrice.Req =>
      sender ! GetGasPrice.Res(gasPrice)

    case block: BlockGasPrices =>
      if (blocks.size >= blockSize && block.height >= blocks.head.height) {
        blocks = blocks.drop(1)
      }
      blocks = blocks.:+(block).sortWith(_.height < _.height)
      calculateGasPrices
  }

  def calculateGasPrices = {
    val gasPrices = blocks.flatMap(_.gasPrices).sortWith(_ > _)
    val excludeAmount = gasPrices.size * excludePercent / 100
    val gasPricesInUse = gasPrices.drop(excludeAmount).dropRight(excludeAmount)
    if (gasPricesInUse.nonEmpty) {
      gasPrice = gasPricesInUse.sum / gasPricesInUse.size
    }
  }
}
