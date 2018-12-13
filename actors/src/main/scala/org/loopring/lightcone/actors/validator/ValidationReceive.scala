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

package org.loopring.lightcone.actors.validator

import akka.actor._
import akka.actor.Actor.Receive
import akka.event.LoggingReceive

object ValidationReceive {
  def apply(validator: PartialFunction[Any, Any])(r: Receive)(
    implicit
    context: ActorContext
  ) = new ValidationReceive(validator, r)

  def Logging(validator: PartialFunction[Any, Any])(r: Receive)(
    implicit
    context: ActorContext
  ) = LoggingReceive(new ValidationReceive(validator, r))
}

private[validator] class ValidationReceive(
    validator: PartialFunction[Any, Any],
    r: Receive
)(
    implicit
    context: ActorContext
) extends Receive {

  private val validate = validator.lift
  def isDefinedAt(o: Any): Boolean = r.isDefinedAt(o)

  def apply(o: Any): Unit = {
    val updatedMsg = validate(o) match {
      case None      ⇒ o
      case Some(())  ⇒ o
      case Some(msg) ⇒ msg
    }
    r(updatedMsg)
  }
}

