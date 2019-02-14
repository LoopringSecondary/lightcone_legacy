package io.lightcone.relayer.support

import io.lightcone.relayer.base.MapBasedLookup
import io.lightcone.relayer.socket._

trait SocketSupport {
  com:CommonSpec =>
  implicit val listeners = new MapBasedLookup[WrappedDataListener[_]]
  listeners.add(BalanceListener.eventName,new BalanceListener())
  val socketServer =  new SocketServer with SocketRegister
  socketServer.start()
}
