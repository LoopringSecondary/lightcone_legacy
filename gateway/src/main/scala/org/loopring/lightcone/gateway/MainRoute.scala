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

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import org.loopring.lightcone.proto._

trait MainRoute extends RouteSupport {

  val route: Route = {
    path("health") {
      get {
        // parameters(('size.as[Int], 'color ?, 'dangerous ? "no"))
        // .as(XRawOrder)(req => request[XRawOrder](req))
        // } ~
        // get {
        request[XRawOrder](new XRawOrder(tokenS = "afafadf"))
      } ~ post {
        entity(as[XRawOrder])(request[XRawOrder](_))
      }
    }
  }
}
