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

package io.lightcone.ethereum.extractor

import scala.concurrent.{ExecutionContext, Future}

trait EventExtractor[S, +E] {
  def extractEvents(source: S): Future[Seq[E]]
}

object EventExtractor {

  // Constructs a new EventExtractor with a given list of sub-EventExtractors and
  // invokes them in the given order. Duplicatd events will be removed.
  def apply[S, E](
      extractors: EventExtractor[S, E]*
    )(
      implicit
      ec: ExecutionContext
    ) = new EventExtractor[S, E] {

    def extractEvents(source: S): Future[Seq[E]] =
      Future
        .sequence(extractors.map(_.extractEvents(source)))
        .map(_.flatten.distinct)
  }
}
