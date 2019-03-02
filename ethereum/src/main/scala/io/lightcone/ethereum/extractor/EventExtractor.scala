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

  def apply[S, E](
      extractors: EventExtractor[S, E]*
    )(
      implicit
      ec: ExecutionContext
    ): EventExtractor[S, E] =
    new Composer[S, E](extractors)

  private[extractor] class Composer[S, E](
      extractors: Seq[EventExtractor[S, E]]
    )(
      implicit
      ec: ExecutionContext)
      extends EventExtractor[S, E] {

    def extractEvents(source: S): Future[Seq[E]] =
      for {
        events <- Future.sequence {
          extractors.map(_.extractEvents(source))
        }
      } yield events.flatten
  }
}
