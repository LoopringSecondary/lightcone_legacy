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

package io.lightcone.relayer.socketio

import com.corundumstudio.socketio.protocol.JacksonJsonSupport
import com.corundumstudio.socketio._
import com.corundumstudio.socketio.listener.DataListener
import com.typesafe.config.Config
import com.fasterxml.jackson.module.scala.DefaultScalaModule

class SocketServer(
  )(
    implicit
    val config: Config,
    val balanceListener: DataListener[SubcribeBalanceAndAllowance],
    val txListener: DataListener[SubcribeTransaction]) {

  val selfConfig = config.getConfig("socketio")
  val socketConfig = new Configuration()
  socketConfig.setHostname(selfConfig.getString("host"))
  socketConfig.setPort(selfConfig.getInt("port"))
  socketConfig.setJsonSupport(new JacksonJsonSupport(DefaultScalaModule))

  val server = new SocketIOServer(socketConfig)

  server.addEventListener(
    BalanceListener.eventName,
    classOf[SubcribeBalanceAndAllowance],
    balanceListener
  )

  server.addEventListener(
    TransactionListener.eventName,
    classOf[SubcribeTransaction],
    txListener
  )

  def start(): Unit = server.start()
}
