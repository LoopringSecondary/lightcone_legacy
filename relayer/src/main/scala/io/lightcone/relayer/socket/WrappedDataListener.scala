package io.lightcone.relayer.socket

import akka.actor.ActorSystem
import com.corundumstudio.socketio.{AckRequest, SocketIOClient}
import com.corundumstudio.socketio.listener.DataListener

import scala.concurrent.ExecutionContext

class WrappedDataListener[R,T]()(implicit system:ActorSystem, ec:ExecutionContext) extends DataListener[T]{

 val clients = Seq.empty[WrappedSocketClient[R,T]]

  def onData(client: SocketIOClient, data: T, ackSender: AckRequest): Unit = {



  }

}
