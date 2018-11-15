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

package org.loopring.lightcone.actors.managing

import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import akka.actor._
import akka.cluster._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives
import scalapb.json4s.JsonFormat
import scala.concurrent.Future
import org.json4s.{ DefaultFormats, jackson }
import org.loopring.lightcone.proto._
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import com.typesafe.config.Config
import scala.concurrent.duration._
import akka.http.scaladsl.model.StatusCodes
import org.loopring.lightcone.actors.routing._
import org.loopring.lightcone.proto.deployment._
import com.google.protobuf.any.Any

class NodeHttpServer(
  config: Config,
  nodeManager: ActorRef)(
  implicit
  val cluster: Cluster,
  implicit val materializer: ActorMaterializer)
  extends Directives
  with Json4sSupport {

  implicit val system = cluster.system
  implicit val context = system.dispatcher
  implicit val serialization = jackson.Serialization
  implicit val formats = DefaultFormats
  implicit val timeout = Timeout(1 seconds)

  lazy val route =
    pathPrefix("stats") {
      concat(
        pathEnd {
          concat(
            get {
              val f = nodeManager ? Msg("get_stats")
              complete(f.mapTo[LocalStats])
            })
        })
    } ~ pathPrefix("settings") {
      concat(
        pathEnd {
          concat(
            post {
              entity(as[DynamicSettings]) { c ⇒
                nodeManager ! UploadDynamicSettings(Some(c))
                complete(c)
              }
            } ~ get {
              complete(NodeData.dynamicSettings)
            })
        })
    }

  Http().bindAndHandle(route, "localhost",
    config.getInt("node-manager.http.port"))
}
