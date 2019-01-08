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
import com.google.inject.name.Named
import com.typesafe.config.Config
import org.loopring.lightcone.persistence.dals.{
  BlockchainScanRecordDal,
  BlockchainScanRecordDalImpl
}
import org.loopring.lightcone.proto._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable.Map

class BlockchainScanRecordServiceImpl @Inject()(
    implicit val dbConfig: DatabaseConfig[JdbcProfile],
    val config: Config,
    @Named("db-execution-context") val ec: ExecutionContext)
    extends BlockchainScanRecordService {

  val dals: Map[Int, BlockchainScanRecordDal] = Map()

  private def getDal(owner: String): BlockchainScanRecordDal = {
    val separateIndex =
      Math.abs(
        owner.hashCode % config.getInt("separate.blockchain_scan_record")
      )
    if (!dals.contains(separateIndex)) {
      dals += (separateIndex -> new BlockchainScanRecordDalImpl(separateIndex))
    }
    dals(separateIndex)
  }

  def saveRecord(
      data: BlockchainRecordData
    ): Future[PersistBlockchainRecord.Res] = {
    getDal(data.owner).saveRecord(data)
  }

  def getRecordsByOwner(
      owner: String,
      sort: SortingType,
      paging: CursorPaging
    ): Future[Seq[BlockchainRecordData]] = {
    getDal(owner).getRecordsByOwner(owner, sort, paging)
  }
}
