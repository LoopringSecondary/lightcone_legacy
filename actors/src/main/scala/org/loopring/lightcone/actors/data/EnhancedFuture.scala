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

package org.loopring.lightcone.actors.data

import scala.concurrent._
import scala.reflect.ClassTag
import org.loopring.lightcone.core.base.ErrorException
import org.loopring.lightcone.proto.XError
import org.loopring.lightcone.proto.XErrorCode._

class EnhancedFuture(f: Future[_])(implicit ec: ExecutionContext) {
  def mapAo[S](implicit tag: ClassTag[S]): Future[S] = f map {
    case m: S        ⇒ m
    case err: XError ⇒ throw ErrorException(err)
    case other ⇒ throw ErrorException(
      XError(ERR_INTERNAL_UNKNOWN, s"message not expected ${other.getClass.getName}")
    )
  }
}
