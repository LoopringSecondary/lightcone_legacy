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
import akka.pattern.ask
import io.lightcone.relayer.base._
import io.lightcone.core.ErrorCode._
import io.lightcone.core._
import io.lightcone.relayer.base._
import io.lightcone.relayer.splitmerge._
import scala.concurrent._
import akka.util.Timeout

// Owner: Daniel
object MessageValidationActor {

  def apply(
      validator: MessageValidator,
      destinationName: String,
      name: String
    )(
      implicit
      system: ActorSystem,
      splitMergerProvider: SplitMergerProvider,
      ec: ExecutionContext,
      timeout: Timeout,
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
    val splitMergerProvider: SplitMergerProvider,
    val ec: ExecutionContext,
    val timeout: Timeout,
    val actors: Lookup[ActorRef])
    extends Actor
    with ActorLogging {

  @inline private def destinationActor = actors.get(destinationName)
  @inline private val validate = validator.validate.lift

  override def receive: Receive = {
    case originalReq =>
      val _sender = sender
      for {
        req <- validate(originalReq).getOrElse {
          Future.failed(
            ErrorException(
              ERR_UNEXPECTED_ACTOR_MSG,
              s"unexpected msg of ${originalReq.getClass.getName}"
            )
          )
        }
        _ = if (req != originalReq)
          log.debug(s"request rewritten from\n\t${originalReq} to\n\t${req}")

        _ <- splitMergerProvider.get(req) match {
          case None =>
            destinationActor.tell(req, _sender)
            Future.unit

          case Some(sm) =>
            for {
              subResponses <- Future.sequence {
                sm.splitRequest(req).map(destinationActor ? _)
              }
              res = sm.mergeResponses(subResponses)
              _ = _sender ! res
            } yield Unit
        }
      } yield Unit
  }
}
