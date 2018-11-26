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

package org.loopring.lightcone.core.cache.actorcache

import scala.concurrent.ExecutionContext
import akka.actor._
import scala.concurrent._
import akka.pattern.ask
import akka.util.Timeout
import scala.reflect.Manifest

class ActorCaching(
    cacheActor: ActorRef,
    sourceActor: ActorRef
)(implicit
    timeout: Timeout,
    ec: ExecutionContext
) {

  def askFor[R, T: Manifest, C](req: R)(
    implicit
    facilitator: ActorCachingFacilitator[R, T, C]
  ) = {

    for {
      cachedResp ← (cacheActor ? req).mapTo[T]
      uncachedRespOpt ← facilitator.genSourceRequest(req, cachedResp) match {
        case Some(sourceReq) ⇒ (sourceActor ? sourceReq).mapTo[T].map(Some(_))
        case None            ⇒ Future.successful(None)
      }

      cacheReqOpt: Option[C] = uncachedRespOpt
        .map(facilitator.genCacheRequest(req, _)).flatten

      _ = cacheReqOpt match {
        case Some(cacheReq) ⇒ cacheActor ! cacheReq
        case None           ⇒
      }

      merged = uncachedRespOpt match {
        case Some(uncachedResp) ⇒
          facilitator.mergeResponses(req, cachedResp, uncachedResp)
        case None ⇒ cachedResp
      }

    } yield merged
  }
}
