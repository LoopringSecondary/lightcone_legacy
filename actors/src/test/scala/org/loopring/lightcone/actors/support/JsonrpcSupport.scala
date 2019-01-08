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

package org.loopring.lightcone.actors.support

import akka.actor.Props
import org.loopring.lightcone.actors.RpcBinding
import org.loopring.lightcone.actors.entrypoint.EntryPointActor
import org.loopring.lightcone.actors.jsonrpc.JsonRpcServer

import scala.concurrent.Future

trait JsonrpcSupport {
  my: CommonSpec =>
  actors.add(
    EntryPointActor.name,
    system.actorOf(Props(new EntryPointActor()), EntryPointActor.name))

  val server = new JsonRpcServer(config, actors.get(EntryPointActor.name)) with RpcBinding
  Future { server.start }
  Thread.sleep(5000)
}
