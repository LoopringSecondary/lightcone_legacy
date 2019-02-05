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

package org.loopring.lightcone.persistence.service

import com.google.inject.Inject
import org.loopring.lightcone.persistence.dals._
import org.loopring.lightcone.core._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.core._
import scala.concurrent.{ExecutionContext, Future}

class RingServiceImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    ringDal: RingDal)
    extends RingService {

  def saveRing(ring: Ring): Future[ErrorCode] =
    ringDal.saveRing(ring)

  def saveRings(rings: Seq[Ring]) = ringDal.saveRings(rings)

  def getRings(request: GetRings.Req): Future[Seq[Ring]] =
    ringDal.getRings(request)

  def countRings(request: GetRings.Req): Future[Int] =
    ringDal.countRings(request)

  def obsolete(height: Long): Future[Unit] = ringDal.obsolete(height)
}
