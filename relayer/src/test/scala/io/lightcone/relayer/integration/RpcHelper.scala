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
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern._
import akka.util.Timeout
import io.lightcone.core.ErrorException
import io.lightcone.relayer.integration.intergration._
import org.scalatest.{Assertion, Matchers}
import org.scalatest.matchers.{MatchResult, Matcher}
import org.slf4s.Logging
import scalapb.GeneratedMessage

import scala.concurrent._

trait RpcHelper extends Logging {
  helper: Matchers =>

  implicit class RichRequest[T <: GeneratedMessage](req: T) {

    def expectUntil[R <: GeneratedMessage](
        matcher: Matcher[R],
        expectTimeout: Timeout = timeout
      )(
        implicit
        system: ActorSystem,
        ec: ExecutionContext,
        m: Manifest[R]
      ): Assertion = {

      var resOpt: Option[R] = None
      var resMatched = false
      val lastTime = System
        .currentTimeMillis() + expectTimeout.duration.toMillis
      while (!resMatched &&
             System.currentTimeMillis() <= lastTime) {
        val res = Await.result(entryPointActor ? req, expectTimeout.duration)
        resOpt = Some(res.asInstanceOf[R])
        res.getClass should be(m.runtimeClass)
        resMatched = matcher(resOpt.get).matches
        if (!resMatched) {
          Thread.sleep(200)
        }
      }
      if (resOpt.isEmpty) {
        throw new Exception(
          s"Timed out waiting for result of req:${req} "
        )
      } else {
        //最好判断，便于返回未匹配的信息
        resOpt.get should matcher
      }
    }

    def expect[R <: GeneratedMessage](
        matcher: Matcher[R]
      )(
        implicit
        timeout: Timeout,
        system: ActorSystem,
        ec: ExecutionContext
      ): Assertion = {
      val res = Await
        .result(entryPointActor ? req, timeout.duration) match {
        case err: ErrorException =>
          throw err
        case m => m.asInstanceOf[R]
      }
      res should matcher
    }

  }

}
