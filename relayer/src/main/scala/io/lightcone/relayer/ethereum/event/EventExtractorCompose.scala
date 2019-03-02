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

package io.lightcone.relayer.ethereum.event

import io.lightcone.core.MetadataManager
import io.lightcone.relayer.data.RawBlockData

import scala.concurrent.{ExecutionContext, Future}

class DefaultEventExtractor(
    extractors: EventExtractor*
  )(
    implicit
    val ec: ExecutionContext,
    val metadataManager: MetadataManager)
    extends EventExtractor {

  def extractEvents(block: RawBlockData): Future[Seq[AnyRef]] =
    for {
      events <- Future.sequence {
        extractors.map(_.extractEvents(block))
      }
    } yield events.flatten

}
