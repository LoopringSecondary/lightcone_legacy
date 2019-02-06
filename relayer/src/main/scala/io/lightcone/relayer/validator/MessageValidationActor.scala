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

package io.lightcone.relayer.validator

import akka.actor._
import akka.util.Timeout
import io.lightcone.relayer.base._
import io.lightcone.core.ErrorCode._
import io.lightcone.proto._
import io.lightcone.core._
import io.lightcone.lib._
import io.lightcone.relayer.base.safefuture._

import scala.concurrent._

// Owner: Daniel
object MessageValidationActor {

  def apply(
      validator: MessageValidator,
      destinationName: String,
      name: String
    )(
      implicit
      system: ActorSystem,
      ec: ExecutionContext,
      actors: Lookup[ActorRef]
    ): ActorRef =
    system.actorOf(
      Props(new MessageValidationActor(destinationName, validator)),
      name
    )
}

class MessageValidationActor(
    destinationName: String,
    validator: MessageValidator
  )(
    implicit
    val ec: ExecutionContext,
    val actors: Lookup[ActorRef])
    extends Actor
    with ActorLogging {

  private def destinationActor = actors.get(destinationName)
  private val validate = validator.validate.lift

  override def receive: Receive = {
    case msg =>
      val f = for {
        validatedMsg <- validate(msg).getOrElse {
          Future.failed(
            ErrorException(
              ERR_UNEXPECTED_ACTOR_MSG,
              s"unexpected msg of ${msg.getClass.getName}"
            )
          )
        }
        _ = if (validatedMsg != msg)
          log.debug(s"request rewritten from\n\t${msg} to\n\t${validatedMsg}")
      } yield validatedMsg

      f.forwardTo(destinationActor, sender)
  }
}
