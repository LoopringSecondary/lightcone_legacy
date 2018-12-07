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

package org.loopring.lightcone.gateway_bak.socketio

import akka.actor.{ ActorSystem, Props }
import com.corundumstudio.socketio.listener.DataListener
import com.corundumstudio.socketio.{ AckRequest, Configuration }
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.loopring.lightcone.gateway_bak.jsonrpc.JsonRpcServer
import org.slf4s.Logging

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class SocketIOServer(
    jsonRpcServer: JsonRpcServer,
    eventBindings: EventBindings
)(
    implicit
    system: ActorSystem
) extends Object with Logging {

  implicit val ex = system.dispatcher

  private val config = system.settings.config
  private val port = config.getInt("jsonrpc.socketio.port")
  private val pathName = config.getString("jsonrpc.socketio.path")
  private val mapper = new ObjectMapper().registerModule(DefaultScalaModule)

  private val ioConfig = {
    val _config = new Configuration
    _config.setHostname("0.0.0.0")
    _config.setPort(port)
    _config.setMaxFramePayloadLength(1024 * 1024)
    _config.setMaxHttpContentLength(1024 * 1024)
    _config.getSocketConfig.setReuseAddress(true)
    _config
  }

  private val router = system.actorOf(
    Props[SocketIOServerRouter],
    "socketio_router"
  )

  def start() {
    val server = new IOServer(ioConfig)
    server.addConnectListener(new ConnectionListener)
    server.addDisconnectListener(new DisconnectionListener)

    server.addEventListener(
      pathName,
      classOf[java.util.Map[String, Any]],
      new DataListener[java.util.Map[String, Any]] {

        override def onData(
          client: IOClient,
          data: java.util.Map[String, Any],
          ackSender: AckRequest
        ) {
          val event = data.get("method").toString
          val json = mapper.writeValueAsString(data)
          log.info(s"client: ${client.getRemoteAddress}, request: ${data}")
          SocketIOClient.add(client, event, json)
          invoke(json).foreach(ackSender.sendAckData(_))
        }
      }
    )

    router ! StartBroadcast(
      this,
      eventBindings,
      config.getInt("jsonrpc.socketio.pool")
    )

    server.start
    log.info(s"socketio server started @ ${port}")
  }

  private[socketio] def invoke(json: String) = Await.result(
    jsonRpcServer.handleRequest(json),
    Duration.Inf
  ).map { resp ⇒
      val respMap = mapper.readValue(
        resp,
        classOf[java.util.Map[String, Any]]
      )
      log.trace(s"socketio rpc response: ${respMap}")
      respMap
    }

}

