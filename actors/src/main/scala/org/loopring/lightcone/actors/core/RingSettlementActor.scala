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

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.actor._
import akka.cluster.sharding._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.ethereum._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto._
import org.web3j.crypto.Credentials
import org.web3j.utils.Numeric

import scala.concurrent._

// main owner: 李亚东
object RingSettlementActor extends ShardedEvenly {
  val name = "ring_settlement"

  def startShardRegion(
    )(
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
      entityProps = Props(new RingSettlementActor()),
      settings = ClusterShardingSettings(system).withRole(name),
      messageExtractor = messageExtractor
    )
  }
}

class RingSettlementActor(
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule)
    extends Actor
    with ActorLogging
    with RepeatedJobActor {

  //防止一个tx中的订单过多，超过 gaslimit
  private val maxRingsInOneTx = 10
  implicit val ringContext: XRingBatchContext =
    XRingBatchContext(
      lrcAddress = config.getString("ring_settlement.lrc_address"),
      feeRecipient = config.getString("ring_settlement.fee_recipient"),
      miner = config.getString("miner"),
      transactionOrigin = config.getString("transaction_origin"),
      minerPrivateKey = config.getString("miner_privateKey")
    )
  implicit val credentials: Credentials =
    Credentials.create(config.getString("transaction_origin_private_key"))

  val protocolAddress: String =
    config.getString("loopring-protocol.protocol-address")
  val repeatedJobs = Nil

  private val ready = new AtomicBoolean(false)
  private val nonce = new AtomicInteger(0)
  // 维护balance, 当余额不足的时候停止接收新的任务
  private var  minerBalance = BigInt(10).pow(18)
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
        validNonce ← getNonce()
        txData = getSignedTxData(
          inputData,
          validNonce,
          req.gasLimit,
          req.gasPrice,
          to = protocolAddress
        )
        hash ← (ethereumAccessActor ? XSendRawTransactionReq(txData))
          .mapAs[XSendRawTransactionRes]
          .map(_.result)
      } yield {
        //TODO(yadong) 把提交的环路记录到数据库,提交失败以后的处理
        minerBalance = minerBalance.min(BigInt(req.gasLimit.toByteArray) * BigInt(req.gasPrice.toByteArray))
        hash
      }
  }

  def getNonce(): Future[Int] = {
    if (ready.get()) {
      Future.successful(nonce.getAndIncrement())
    } else {
      for {
        validNonce ← (ethereumAccessActor ? XGetNonceReq(
          owner = ringContext.transactionOrigin,
          tag = "latest"
        )).mapAs[XGetNonceRes]
          .map(_.result)
      } yield {
        if (ready.compareAndSet(false, true)) {
          nonce.set(Numeric.toBigInt(validNonce).intValue())
        }
        nonce.getAndIncrement()
      }
    }
  }

}
