package org.loopring.lightcone.actors.core

import akka.actor._
import akka.cluster.sharding._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.lib.TimeProvider
import org.loopring.lightcone.persistence.DatabaseModule

import scala.concurrent.ExecutionContext

object OHLCRawDataPersistenceActor extends ShardedEvenly {
 val name = "OHLCRawData_persistence"

  def start(
             implicit
             system: ActorSystem,
             config: Config,
             ec: ExecutionContext,
             timeProvider: TimeProvider,
             timeout: Timeout,
             actors: Lookup[ActorRef],
             dbModule: DatabaseModule,
             deployActorsIgnoringRoles: Boolean
           ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shards")
    entitiesPerShard = selfConfig.getInt("entities-per-shard")

    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new OHLCRawDataPersistenceActor()),
      settings = ClusterShardingSettings(system).withRole(roleOpt),
      messageExtractor = messageExtractor
    )
  }
}


class OHLCRawDataPersistenceActor extends ActorWithPathBasedConfig(OHLCRawDataPersistenceActor.name){




}
