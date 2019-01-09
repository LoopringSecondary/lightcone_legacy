package org.loopring.lightcone.actors.ethereum

import akka.actor.ActorRef
import org.loopring.lightcone.actors.base.Lookup

trait Processor[R] {
  val actors: Lookup[ActorRef]
  val recipient:String

  def receiver:ActorRef = actors.get(recipient)

  def process(data:R)= receiver  !  data
}
