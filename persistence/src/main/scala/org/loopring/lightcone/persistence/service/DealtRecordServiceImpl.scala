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
import org.loopring.lightcone.persistence.dals.DealtRecordDal
import org.loopring.lightcone.proto.{DealtRecord, ErrorCode}

import scala.concurrent.{ExecutionContext, Future}

class DealtRecordServiceImpl @Inject()(
    implicit
    dealtRecordDal: DealtRecordDal,
    val ec: ExecutionContext)
    extends DealtRecordService {

  def saveDealtRecord(
      record: DealtRecord
    ): Future[Either[DealtRecord, ErrorCode]] =
    dealtRecordDal.saveDealtRecord(record).map { r =>
      if (r.error == ErrorCode.ERR_NONE) {
        Left(r.record.get)
      } else {
        Right(r.error)
      }
    }

}
