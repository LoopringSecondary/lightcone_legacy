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

import akka.actor._
import akka.cluster.Cluster
import com.google.inject.name.Named
import com.google.inject.{ AbstractModule, Inject, Provides }
import net.codingwell.scalaguice.ScalaModule
import org.loopring.lightcone.biz.BizCoreModule
import org.loopring.lightcone.core.{ TokenValueEstimator, TokenValueEstimatorImpl }
//import akka.cluster._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.loopring.lightcone.actors.actor._
import org.loopring.lightcone.actors.managing.ClusterManager
import org.loopring.lightcone.core.{ DustOrderEvaluator, DustOrderEvaluatorImpl }
//import slick.basic.DatabaseConfig
//import slick.jdbc.JdbcProfile
//import com.google.inject._
//import com.google.inject.name._
import com.typesafe.config.Config
//import net.codingwell.scalaguice._
//import org.loopring.lightcone.core.accessor._
//import org.loopring.lightcone.core.actors._
//import org.loopring.lightcone.core.cache._
//import org.loopring.lightcone.core.database._
//import org.loopring.lightcone.core.order._
//import org.loopring.lightcone.core.block._
//import org.loopring.lightcone.lib.abi._
//import org.loopring.lightcone.lib.cache._
// import org.loopring.lightcone.lib.time._
//import org.loopring.lightcone.proto.token._
//import redis._
import scala.concurrent._
import scala.concurrent.duration._

class CoreModule(config: Config)
  extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    implicit val system = ActorSystem("Lightcone", config)
    implicit val cluster = Cluster(system)

    bind[Config].toInstance(config)
    //    bind[DatabaseConfig[JdbcProfile]]
    //      .toInstance(DatabaseConfig.forConfig("db.default", config))
    //
    bind[ActorSystem].toInstance(system)
    bind[Cluster].toInstance(cluster)
    bind[ActorMaterializer].toInstance(ActorMaterializer())
    //
    bind[ExecutionContext].annotatedWithName("system-dispatcher").toInstance(system.dispatcher)
    //        bind[ExecutionContext].annotatedWithName("db-execution-context")
    //          .toInstance(ExecutionContext.fromExecutor(ForkJoinPool.commonPool()))
    //
    //    bind[TimeProvider].to[LocalSystemTimeProvider]
    //    bind[TimeFormatter].to[SimpleTimeFormatter]
    //
    bind[Timeout].toInstance(new Timeout(config.getInt("behaviors.future-wait-timeout") seconds))
    //    bind[Erc20Abi].toInstance(new Erc20Abi(config.getString("abi.erc20")))
    //    bind[WethAbi].toInstance(new WethAbi(config.getString("abi.weth")))
    //    bind[LoopringAbi].toInstance(new LoopringAbi(config.getString("abi.loopring")))
    //    bind[EthClient].to[EthClientImpl].in[Singleton]
    //
    //    val httpFlow = Http()
    //      .cachedHostConnectionPool[Promise[HttpResponse]](
    //        host = config.getString("ethereum.host"),
    //        port = config.getInt("ethereum.port")
    //      )
    //
    //    bind[HttpFlow].toInstance(httpFlow)
    //
    //    bind[Int].annotatedWith(Names.named("ethereum_conn_queuesize"))
    //      .toInstance(config.getInt("ethereum.queueSize"))
    //
    //    bind[RedisCluster].toProvider[cache.RedisClusterProvider].in[Singleton]
    //
    //    bind[OrderWriteHelper].to[OrderWriteHelperImpl]
    //    bind[OrderValidator].to[OrderValidatorImpl]
    //    bind[OrderDatabase].to[MySQLOrderDatabase]
    //    bind[OrderCache].to[cache.OrderRedisCache]
    //    bind[OrderAccessHelper].to[OrderAccessHelperImpl]
    bind[Double].annotatedWithName("dust_threshold").toInstance(double2Double(0.5))

    // TODO(Toan) 这里启动报错 先这样处理
    // bind[MarketManager].to[MarketManagerImpl]
    implicit val tokenValueEstimator: TokenValueEstimator = new TokenValueEstimatorImpl
    bind[DustOrderEvaluator].toInstance(new DustOrderEvaluatorImpl(0))

    install(new BizCoreModule)
  }

  //  @Provides
  //  @Singleton
  //  @Named("node_manager")
  //  def getNodeManager(injector: Injector, config: Config)(implicit
  //    cluster: Cluster,
  //    materializer: ActorMaterializer
  //  ) = {
  //
  //    cluster.system.actorOf(
  //      Props(new managing.NodeManager(injector, config)), "node_manager"
  //    )
  //  }
  //
  @Provides
  @Named("ethereum_access_actor")
  def getEthereumAccessActorProps()(implicit
    @Named("system-dispatcher") ec: ExecutionContext,
    timeout: Timeout) = {
    Props(new EthereumAccessActor()) // .withDispatcher("ring-dispatcher")
  }

  //  @Provides
  //  @Named("market_managing_actor")
  //  def getMarketManagingActorProps(manager: MarketManager)(implicit
  //    @Named("system-dispatcher") ec: ExecutionContext,
  //    timeout: Timeout) = {
  //    Props(new MarketManagingActor(manager)) // .withDispatcher("ring-dispatcher")
  //  }

  @Provides
  @Named("order_fill_history_actor")
  def getOrderFillHistoryActorProps()(implicit
    @Named("system-dispatcher") ec: ExecutionContext,
    timeout: Timeout) = {
    Props(new OrderFillHistoryActor()) // .withDispatcher("ring-dispatcher")
  }

  @Provides
  @Named("order_managing_actor")
  def getOrderManagingActorProps()(implicit
    @Named("system-dispatcher") ec: ExecutionContext,
    @Inject dustOrderEvaluator: DustOrderEvaluator,
    timeout: Timeout) = {
    //TODO(xiaolu) confirm with others
    Props(new OrderManagingActor(""))
  }

  @Provides
  @Named("ring_submit_actor")
  def getRingSubmitActorProps()(implicit
    @Named("system-dispatcher") ec: ExecutionContext,
    timeout: Timeout) = {
    //TODO(xiaolu) confirm with others
    Props(new RingSubmitActor(""))
  }

  @Provides
  @Named("cluster_manager")
  def getClusterManagerProps()(implicit
    @Named("system-dispatcher") ec: ExecutionContext,
    timeout: Timeout) = {
    Props(new ClusterManager()) // .withDispatcher("ring-dispatcher")
  }
}
