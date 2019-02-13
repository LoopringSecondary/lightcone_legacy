package io.lightcone.relayer.socket

import akka.actor.{ActorSystem, Cancellable}
import com.corundumstudio.socketio.SocketIOClient
import scala.concurrent.duration._


import scala.concurrent.ExecutionContext

abstract class  WrappedSocketClient[R,T](eventName:String,client:SocketIOClient,req:R)(implicit system:ActorSystem,ec:ExecutionContext) {

  var scheduler:Cancellable = null

  def sendMessage = {
    if(scheduler != null){
      scheduler.cancel()
    }
    scheduler = system.scheduler.schedule(0 second, 1 seconds,new Runnable {
      override def run(): Unit = {
        client.sendEvent(eventName,queryData)
      }
    })
  }

  def queryData:T

}
