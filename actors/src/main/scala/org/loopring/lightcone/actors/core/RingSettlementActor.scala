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

import java.util.concurrent.atomic.AtomicInteger

import akka.actor._
import akka.cluster.sharding._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.google.protobuf.ByteString
import com.typesafe.config.Config
import org.loopring.lightcone.lib._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.proto.XErrorCode._
import org.loopring.lightcone.proto.XOrderStatus._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.ethereum._
import org.loopring.lightcone.persistence.DatabaseModule
import org.web3j.crypto.Credentials
import org.web3j.utils.Numeric

import scala.concurrent._
import scala.annotation.tailrec

// main owner: 李亚东
object RingSettlementActor extends ShardedEvenly {
  val name = "ring_settlement"

  def startShardRegion(nonce :Int = 0)(
      implicit system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule
    ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shards")
    entitiesPerShard = selfConfig.getInt("entities-per-shard")

    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new RingSettlementActor(nonce)),
      settings = ClusterShardingSettings(system).withRole(name),
      messageExtractor = messageExtractor
    )
  }
}

class RingSettlementActor(initialNonce :Int = 0)(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule)
    extends ActorWithPathBasedConfig(RingSettlementActor.name)
    with RepeatedJobActor {

  //防止一个tx中的订单过多，超过 gaslimit
  private val maxRingsInOneTx = 10
  private val nonce = new AtomicInteger(initialNonce)
  //TODO(yadong)
  implicit val ringContext: XRingBatchContext = XRingBatchContext()
  implicit val credentials: Credentials =
    Credentials.create(config.getString("private_key"))
  val protocolAddress = config.getString("loopring-protocol.protocol-address")
  val repeatedJobs = Nil

  private def ethereumAccessActor = actors.get(EthereumAccessActor.name)
  private def gasPriceActor = actors.get(GasPriceActor.name)

  import ethereum._

  override def receive: Receive = super.receive orElse LoggingReceive {
    case req: XSettleRingsReq =>
      for {
        rawOrders ← Future.sequence(req.rings.map { xOrderRing ⇒
          dbModule.orderService.getOrders(
            Seq(
              xOrderRing.maker.get.order.get.id,
              xOrderRing.taker.get.order.get.id
            )
          )
        })
        xRingBatch = RingBatchGeneratorImpl.generateAndSignRingBatch(rawOrders)
        inputData = RingBatchGeneratorImpl.toSubmitableParamStr(xRingBatch)
        txData = getSignedTxData(
          inputData,
          nonce.get(),
          req.gasLimit,
          req.gasPrice,
          to = protocolAddress
        )
        hash ← (ethereumAccessActor ? XSendRawTransactionReq(txData))
          .mapAs[XSendRawTransactionRes]
          .map(_.result)
      } yield {
        //TODO(yadong) 把提交的环路记录到数据库,提交失败以后的处理
        hash
      }
    case _ =>
  }
}
