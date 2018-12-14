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

package org.loopring.lightcone.actors.base

import scala.concurrent._
import scala.util.{Failure, Success}
import scala.reflect.ClassTag
import org.loopring.lightcone.core.base.ErrorException
import org.loopring.lightcone.proto.XError
import org.loopring.lightcone.proto.XErrorCode._
import akka.actor._

object safefuture {

  implicit class SafeFutureSupport[T](
      f: Future[T]
    )(
      implicit ec: ExecutionContext,
      ac: ActorContext) {

    // Map a future to a certain type. All other types will throw ErrorException.
    def mapAs[S](implicit tag: ClassTag[S]): Future[S] = f map {
      case m: S        => m
      case err: XError => throw ErrorException(err)
      case other =>
        throw ErrorException(
          XError(
            ERR_INTERNAL_UNKNOWN,
            s"unexpected msg ${other.getClass.getName}"
          )
        )
    }

    // Forward the future to a receiver but will send back an XError to sender in case of exception.
    def forwardTo(
        recipient: ActorRef
      )(
        implicit sender: ActorRef = Actor.noSender
      ): Future[T] = {
      f onComplete {
        case Success(r) => recipient forward r
        case Failure(f) =>
          f match {
            case e: ErrorException => sender ! e.error
            case e: Throwable      => throw e
          }
      }
      f
    }

    // Send the future to a receiver but will send back an XError to sender in case of exception.
    def sendTo(
        recipient: ActorRef
      )(
        implicit sender: ActorRef = Actor.noSender
      ): Future[T] = {
      f onComplete {
        case Success(r) => recipient ! r
        case Failure(f) =>
          f match {
            case e: ErrorException => sender ! e.error
            case e: Throwable      => throw e
          }
      }
      f
    }
  }
}
