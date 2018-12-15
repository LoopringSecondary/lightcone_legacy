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

package org.loopring.lightcone.gateway

import org.loopring.lightcone.gateway.jsonrpc._
import org.loopring.lightcone.proto._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import scalapb.json4s.JsonFormat
import scala.reflect.runtime.universe._

import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import akka.actor._
import akka.util.Timeout

// trait MainRoute extends AbstractJsonRpcModule with JsonSupport {
//   val requestHandler: ActorRef

//   implicit val timeout: Timeout

//   val route: Route = {
//     path("api") {
//       post {
//         entity(as[JsonRpcRequest]) { req =>
//           val method = req.method

//           val binded = getBinded(method)
//           val params = req.params.map(binded.strToReq).get

//           onSuccess(requestHandler ? req) { resp =>
//             println("resp: " + resp)
//             complete(binded.resToStr(resp))
//           }
//         }
//       }
//     }
//   }
// }
