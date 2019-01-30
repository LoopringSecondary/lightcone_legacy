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

package org.loopring.lightcone.actors.jsonrpc

import org.loopring.lightcone.actors.entrypoint.EntryPointActor
import org.loopring.lightcone.actors.base.Lookup
import com.typesafe.config.Config
import akka.http.scaladsl.server.HttpApp
import akka.actor._
import akka.util.Timeout
import org.loopring.lightcone.core.base.MetadataManager

import scala.io.StdIn
import scala.concurrent.ExecutionContext

// Owner: Daniel
abstract class JsonRpcServer(
    val config: Config,
    val requestHandler: ActorRef
  )(
    implicit
    val system: ActorSystem,
    val timeout: Timeout,
    val metadataManager: MetadataManager,
    val ec: ExecutionContext)
    extends HttpApp {

  val host = config.getString("jsonrpc.http.host")
  val port = config.getInt("jsonrpc.http.port")

  def start = {
    startServer(host, port, system)
    println(s"started jsonrpc at ${host}:${port}")
  }
}
