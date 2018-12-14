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

package org.loopring.lightcone.gateway.service

import akka.actor.ActorRef
import akka.util.Timeout
import com.google.inject.Inject
import com.google.inject.name.Named

import scala.concurrent.Future
import scala.reflect.runtime.universe._

class ApiService @Inject()(
    @Named("entry-point") val entryPointActor: ActorRef
  )(
    implicit val timeout: Timeout)
    extends Object
    with AccountService
    with MarketService
    with OrderService {

  def test(): Future[String] = {
    Future.successful("test")
  }

  val rm = runtimeMirror(this.getClass.getClassLoader)
  val thisMirror = rm.reflect(this)

  val methodMap = (thisMirror.symbol.toType.decls
    .filter(_.isMethod)
    .map { m =>
      m.name.toString → thisMirror.reflectMethod(m.asMethod)
    })
    .toMap

  def handle(req: JsonRpcReq) =
    req.params match {
      case None    => methodMap(req.method)()
      case Some(p) => methodMap(req.method)(p)
    }

}
