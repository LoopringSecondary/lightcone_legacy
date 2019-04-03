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
import akka.actor.ActorSystem
import akka.pattern._
import akka.util.Timeout
import io.lightcone.core.ErrorException
import org.scalatest.Matchers
import org.scalatest.matchers.Matcher
import org.slf4s.Logging
import scalapb.GeneratedMessage
import scala.concurrent.{Await, ExecutionContext}

trait RpcHelper extends Logging {
  helper: Matchers =>

  implicit class RichRequest[T](req: T) {

    def expectUntil[R <: GeneratedMessage](
        matcher: Matcher[R],
        expectTimeout: Timeout = timeout
      )(
        implicit
        system: ActorSystem,
        ec: ExecutionContext,
        m: Manifest[R]
      ) = {

      var resOpt: Option[R] = None
      var resMatched = false
      val lastTime = System
        .currentTimeMillis() + expectTimeout.duration.toMillis
      var errOpt: Option[ErrorException] = None
      while (!resMatched &&
             System.currentTimeMillis() <= lastTime) {
        val res = Await.result(entryPointActor ? req, expectTimeout.duration)
        resOpt = res match {
          case err: ErrorException =>
            if (m.runtimeClass == err.getClass) {
              Some(res.asInstanceOf[R])
            } else {
              errOpt = Some(err)
              None
            }
          case msg =>
            Some(msg.asInstanceOf[R])
        }
        if (resOpt.nonEmpty) {
          println(s"#### res ${res}")
          res.getClass should be(m.runtimeClass)
          resMatched = matcher(resOpt.get).matches
        }
        if (resOpt.isEmpty || !resMatched) {
          Thread.sleep(200)
        }
      }
      if (resOpt.isEmpty) {
        if (errOpt.nonEmpty) {
          throw errOpt.get
        } else {
          throw new Exception(
            s"Timed out waiting for result of req:${req} "
          )
        }
      } else {
        //最好判断，便于返回未匹配的信息
        resOpt.get should matcher
        resOpt.get
      }
    }

    def expect[R](
        matcher: Matcher[R]
      )(
        implicit
        timeout: Timeout,
        system: ActorSystem,
        ec: ExecutionContext,
        m: Manifest[R]
      ) = {
      val res = Await
        .result(entryPointActor ? req, timeout.duration) match {
        case err: ErrorException =>
          if (m.runtimeClass == err.getClass)
            err.asInstanceOf[R]
          else throw err
        case msg => msg.asInstanceOf[R]
      }
      res should matcher
      res
    }

  }

}
