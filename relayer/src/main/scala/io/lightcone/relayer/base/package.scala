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

package io.lightcone.relayer

import akka.actor._
import io.lightcone.core._
import io.lightcone.core.ErrorCode._
import scala.concurrent._
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

// Owner: Daniel

package object base {

  implicit class SafeFutureSupport[T](
      f: Future[T]
    )(
      implicit
      ec: ExecutionContext) {

    // Map a future to a certain type. All other types will throw ErrorException.
    def mapAs[S](implicit tag: ClassTag[S]): Future[S] = f map {
      case m: S       => m
      case err: Error => throw ErrorException(err)
      case other =>
        throw ErrorException(
          Error(
            ERR_INTERNAL_UNKNOWN,
            s"unexpected msg ${other} ${other.getClass.getName}"
          )
        )
    }

    // Forward the future to a receiver but will send back an Error to sender in case of exception.
    // TODO:直接使用forward无法使用，暂时以该方式实现功能，后续可以优化
    def forwardTo(
        recipient: ActorRef,
        orginSender: ActorRef
      )(
        implicit
        sender: ActorRef
      ): Future[T] = {
      f onComplete {
        case Success(r) => recipient.tell(r, orginSender)
        case Failure(failure) =>
          failure match {
            case e: ErrorException =>
              orginSender ! e.error
            case e: Throwable => throw e
          }
      }
      f
    }

    // Send the future to a receiver but will send back an Error to sender in case of exception.
    def sendTo(
        recipient: ActorRef,
        orginSenderOpt: Option[ActorRef] = None
      )(
        implicit
        sender: ActorRef = Actor.noSender
      ): Future[T] = {
      f onComplete {
        case Success(r) => recipient ! r
        case Failure(failure) =>
          failure match {
            case e: ErrorException =>
              orginSenderOpt match {
                case Some(orginSender) => orginSender ! e
                case None              => recipient ! e
              }
            case e: Throwable => throw e
          }
      }
      f
    }
  }
}
