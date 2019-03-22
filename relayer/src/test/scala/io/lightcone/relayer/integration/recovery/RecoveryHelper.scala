package io.lightcone.relayer.integration.recovery

import io.lightcone.core.MarketPair
import io.lightcone.relayer.actors.{MarketManagerActor, MultiAccountManagerActor}
import io.lightcone.relayer.integration._
import io.lightcone.relayer.data._

trait RecoveryHelper {


  private def getEntityNums(name:String) = config.getInt(s"$name.num-of-entities")
  private def getShardNums(name:String) = config.getInt(s"${name}.num-of-shards")

  private def getShardId(entityId:Long,name:String) = {
    val shardStr = s"$name_$entityId"
    (shardStr.hashCode % getShardNums(name)).abs
  }

 private def getAccountManagerEntityId(addr:String) =
    (addr.hashCode % getEntityNums(MultiAccountManagerActor.name)).abs



  private def getMarketManagerEntityId(marketPair:MarketPair) =  marketPair.longId

  private def getShardActorPath(name:String,entityId:Long,shardId:Long) = s"/system/sharding/$name/$shardId/$name_$entityId"

  def getAccountManagerShardActor(addr:String) = {
    val entityId = getAccountManagerEntityId(addr)
    val shardId = getShardId(entityId,MultiAccountManagerActor.name)
    val path  = getShardActorPath(MultiAccountManagerActor.name,entityId,shardId)
    system.actorSelection(path)
  }

  def getMarketManagerShardActor(marketPair: MarketPair)= {
    val entityId = getMarketManagerEntityId(marketPair)
    val shardId = getShardId(entityId,MarketManagerActor.name)
    val path  = getShardActorPath(MarketManagerActor.name,entityId,shardId)
    system.actorSelection(path)
  }


  










}
