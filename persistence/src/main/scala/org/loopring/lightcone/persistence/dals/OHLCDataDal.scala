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

import org.loopring.lightcone.persistence.base.BaseDalImpl

import scala.concurrent.Future
import org.loopring.lightcone.proto._
import org.loopring.lightcone.core.data._

trait OHLCDataDal extends BaseDalImpl[OHLCDataTable, OHLCRawData] {

  // Save a order to the database and returns the saved order and indicate
  def saveData(record: OHLCRawData): Future[PersistOHLCData.Res]

  def getOHLCData(
      marketHash: String,
      interval: Long,
      beginTime: Long,
      endTime: Long
    ): Future[Seq[Seq[Double]]]

}
