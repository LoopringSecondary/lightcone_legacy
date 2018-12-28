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

import org.loopring.lightcone.proto.{BlockData, ErrorCode}
import scala.concurrent.Future

trait BlockService {

  def saveBlock(block: BlockData): Future[ErrorCode]
  def findByHash(hash: String): Future[Option[BlockData]]
  def findByHeight(height: Long): Future[Option[BlockData]]
  def findMaxHeight(): Future[Option[Long]]

  def findBlocksInHeightRange(
      heightFrom: Long,
      heightTo: Long
    ): Future[Seq[(Long, String)]]
  def count(): Future[Int]
  def obsolete(height: Long): Future[Unit]
}
