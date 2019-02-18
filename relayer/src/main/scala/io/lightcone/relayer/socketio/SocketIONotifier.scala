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

import scala.reflect.ClassTag
import com.corundumstudio.socketio._
import com.corundumstudio.socketio.listener.DataListener
import org.slf4s.Logging

abstract class SocketIONotifier[R, E <: AnyRef: ClassTag](
    implicit
    m: Manifest[E])
    extends DataListener[R]
    with Logging {

  val eventName: String

  def shouldNotifyClient(
      subscription: R,
      event: E
    ): Boolean

  def wrapClient(
      client: SocketIOClient,
      subscription: R
    ): SocketIOSubscriber[R, E]

  private val clazz = implicitly[ClassTag[E]].runtimeClass
  private var clients = Seq.empty[SocketIOSubscriber[R, E]]

  def notifyEvent(evt: Any): Unit = {
    clients = clients.filterNot(_.client.isChannelOpen)
    if (clazz.isInstance(evt)) {
      val event = evt.asInstanceOf[E]
      onEvent(event)
      log.debug(s"socketio notifhy: $event")
    } else {
      log.error(s"unexpeceted event: $evt")
    }
  }

  def onData(
      client: SocketIOClient,
      subscription: R,
      ackSender: AckRequest
    ): Unit = {
    if (ackSender.isAckRequested) {
      ackSender.sendAckData(s"$eventName:$subscription events subscribed")
    }

    val wrapped = wrapClient(client, subscription)
    clients = wrapped +: clients.filterNot(_ == wrapped)
  }

  def onEvent(event: E) = {
    val e = transformEvent(event)
    clients
      .filter(client => shouldNotifyClient(client.subscription, e))
      .foreach(_.sendEvent(eventName, e))
  }

  // Override this method to change the event. Normally we should not do this.
  def transformEvent(event: E): E = event
}
