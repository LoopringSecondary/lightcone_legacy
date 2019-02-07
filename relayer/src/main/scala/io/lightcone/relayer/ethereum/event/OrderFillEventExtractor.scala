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

import com.google.inject.Inject
import io.lightcone.relayer.data._
import io.lightcone.core._
import scala.concurrent.{ExecutionContext, Future}

class OrderFillEventExtractor @Inject()(
    implicit
    extractor: RingMinedEventExtractor,
    val ec: ExecutionContext)
    extends EventExtractor[OrderFilledEvent] {

  def extract(block: RawBlockData): Future[Seq[OrderFilledEvent]] =
    extractor
      .extract(block)
      .map(_.filter(_.header.get.txStatus.isTxStatusSuccess).flatMap(_.fills))
}
