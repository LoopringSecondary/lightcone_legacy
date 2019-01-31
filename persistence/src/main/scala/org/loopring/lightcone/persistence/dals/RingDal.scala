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

package org.loopring.lightcone.persistence.dals

import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto._
import scala.concurrent._

trait RingDal extends BaseDalImpl[RingTable, Ring] {
  def saveRing(ring: Ring): Future[ErrorCode]
  def saveRings(rings: Seq[Ring]): Future[Seq[ErrorCode]]
  def getRings(request: GetRings.Req): Future[Seq[Ring]]
  def countRings(request: GetRings.Req): Future[Int]
  def obsolete(height: Long): Future[Unit]
}