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

package io.lightcone.relayer.integration
import akka.actor.{Actor, ActorRef, ActorSystem}
import io.lightcone.relayer.RpcBinding
import io.lightcone.relayer.data.GetAccount
import io.lightcone.relayer.jsonrpc.Method

trait RpcBindingForTest extends RpcBinding {
  val requestHandler: ActorRef = Actor.noSender

  //TODO(HONGYU):应该由代码直接生成
  var methods =
    Map[Class[_], String](GetAccount.Req().getClass -> "get_account")

  override def method(name: String): Method = {
    super.method(name)
  }
}
