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

package io.lightcone.relayer.support

import java.util.concurrent.TimeUnit

import akka.actor.Props
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import io.lightcone.relayer.RpcBinding
import io.lightcone.relayer.entrypoint.EntryPointActor
import io.lightcone.relayer.jsonrpc.{ JsonRpcServer, JsonSupport }
import org.rnorth.ducttape.TimeoutException
import org.rnorth.ducttape.unreliables.Unreliables
import org.testcontainers.containers.ContainerLaunchException

import scala.concurrent.{ Await, Future }

trait JsonrpcSupport extends JsonSupport {
  my: CommonSpec =>
  actors.add(
    EntryPointActor.name,
    system.actorOf(Props(new EntryPointActor()), EntryPointActor.name))

  val server = new JsonRpcServer(config, actors.get(EntryPointActor.name)) with RpcBinding
  Future { server.start }

  //必须等待jsonRpcServer启动完成
  try Unreliables.retryUntilTrue(
    10,
    TimeUnit.SECONDS,
    () => {
      val f = Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            serialization.write("[]")),
          uri = Uri(
            s"http://127.0.0.1:${config.getString("jsonrpc.http.port")}/" +
              s"${config.getString("jsonrpc.endpoint")}/${config.getString("jsonrpc.loopring")}")))
      val res = Await.result(f, timeout.duration)
      res.status.intValue() <= 500
    })
  catch {
    case e: TimeoutException =>
      throw new ContainerLaunchException(
        "Timed out waiting for jsonrpcServer starting.)")
  }

}
