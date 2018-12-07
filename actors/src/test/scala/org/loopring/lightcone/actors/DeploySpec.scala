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

package org.loopring.lightcone.actors

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.cluster.Cluster
import akka.cluster.routing.{ ClusterRouterGroup, ClusterRouterGroupSettings }
import akka.cluster.sharding.{ ClusterSharding, ClusterShardingSettings }
import akka.pattern._
import akka.routing.RoundRobinGroup
import akka.util.Timeout
import com.typesafe.config.{ Config, ConfigFactory }
import org.loopring.lightcone.actors.base.{ Lookup, MapBasedLookup }
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.proto.core._
import org.scalatest.{ FlatSpec, Matchers }
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.entrypoint.EntryPoinActor
import org.loopring.lightcone.actors.persistence.OrderStateActor
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.market.RingIncomeEstimatorImpl
import org.loopring.lightcone.proto.actors._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._
import scala.concurrent.Await

class DeploySpec extends FlatSpec with Matchers {

  "deploy" should "actors are running" in {

    // val GTO = "0x00000000001"
    // val WETH = "0x00000000004"
    // val LRC = "0x00000000002"

    // val GTO_TOKEN = XTokenMetadata(GTO, 10, 0.1, 1.0, "GTO")
    // val WETH_TOKEN = XTokenMetadata(WETH, 18, 0.4, 1000, "WETH")
    // val LRC_TOKEN = XTokenMetadata(LRC, 18, 0.4, 1000, "LRC")

    // info("##### deploy")

    // val order = XOrder(
    //   id = "buy_lrc",
    //   tokenS = WETH_TOKEN.address,
    //   tokenB = LRC_TOKEN.address,
    //   tokenFee = LRC_TOKEN.address,
    //   amountS = BigInt("50000000000000000000"),
    //   amountB = BigInt("10000000000000000000000"),
    //   amountFee = BigInt("10000000000000000000"),
    //   status = XOrderStatus.STATUS_NEW)

    // val actors = new MapBasedLookup[ActorRef]()
    // val system = ActorSystem(
    //   "Lightcone",
    //   ConfigFactory.parseString("akka.remote.artery.canonical.port=" + 2551)
    //     .withFallback(ConfigFactory.load()))
    // implicit val cluster = Cluster(system)
    // implicit val timeout = Timeout(5 second)
    // implicit val ec = system.dispatcher
    // implicit val tmm = new TokenMetadataManager()

    // implicit val tokenValueEstimator: TokenValueEstimator = new TokenValueEstimator()
    // implicit val dustEvaluator: DustOrderEvaluator = new DustOrderEvaluator()

    // val accountManagerActor = ClusterSharding(system).start(
    //   typeName = "AccountManagerActor",
    //   entityProps = Props(new AccountManagerActor(actors, 100, false)),
    //   settings = ClusterShardingSettings(system),
    //   extractEntityId = AccountManagerActor.extractEntityId,
    //   extractShardId = AccountManagerActor.extractShardId)

    // //    val accountManagerActor = system.actorOf(
    // //      ClusterRouterGroup(
    // //        RoundRobinGroup(Nil),
    // //        ClusterRouterGroupSettings(
    // //          totalInstances = Int.MaxValue,
    // //          routeesPaths = List("/system/sharding/" + "AccountManagerActor"),
    // //          allowLocalRoutees = cluster.selfRoles.contains(AccountManagerActor.name),
    // //          AccountManagerActor.name, "dc-default" //支持默认的
    // //        )
    // //      ).props,
    // //      AccountManagerActor.name + "-router"
    // //    )
    // println(s"### cluster ${cluster.selfRoles}")
    // println(s"@@@, ${accountManagerActor.path}")
    // actors.add(AccountManagerActor.name, accountManagerActor)

    // implicit val ringIncomeEstimator = new RingIncomeEstimatorImpl()
    // implicit val timeProvider = new SystemTimeProvider()
    // val marketManagerActor = ClusterSharding(system).start(
    //   typeName = "MarketManagerActor",
    //   entityProps = Props(new MarketManagerActor(actors, XMarketManagerConfig(), false)),
    //   settings = ClusterShardingSettings(system),
    //   extractEntityId = AccountManagerActor.extractEntityId,
    //   extractShardId = AccountManagerActor.extractShardId)

    // val orderbookActor = system.actorOf(
    //   ClusterRouterGroup(
    //     RoundRobinGroup(Nil),
    //     ClusterRouterGroupSettings(
    //       totalInstances = Int.MaxValue,
    //       routeesPaths = List("/user/" + OrderbookManagerActor.name),
    //       allowLocalRoutees = cluster.selfRoles.contains(OrderbookManagerActor.name),
    //       OrderbookManagerActor.name, "dc-default" //支持默认的
    //       )).props,
    //   OrderbookManagerActor.name + "-router")
    // println(s"### cluster ${cluster.selfRoles}")
    // actors.add(OrderbookManagerActor.name, orderbookActor)

    // val entryPointActor = system.actorOf(Props(new EntryPoinActor(actors)), "entry-point")

    // Thread.sleep(1000)
    // marketManagerActor ! XStart(WETH_TOKEN.address + "-" + LRC_TOKEN.address)
    // accountManagerActor ! XStart("address_1")
    // Thread.sleep(1000)

    // var resFuture = entryPointActor ? XSubmitOrderReq(Some(order))

    // var res = Await.result(resFuture.mapTo[XSubmitOrderRes], timeout.duration)
    // info(s"submitOrderRes :${res} ")

    // val resFuture1 = entryPointActor ? XGetOrderbookReq(0, 100)

    // val res2 = Await.result(resFuture1.mapTo[XOrderbook], timeout.duration)
    // info(s"after submit one order, the orderbook is :${res2} ")
  }

}
