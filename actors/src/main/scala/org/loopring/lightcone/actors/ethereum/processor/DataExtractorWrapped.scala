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

package org.loopring.lightcone.actors.ethereum.processor

import org.loopring.lightcone.ethereum.event.DataExtractor
import org.loopring.lightcone.proto.BlockJob

import scala.collection.mutable.ListBuffer

trait DataExtractorWrapped[R] {

  val extractor: DataExtractor[R]
  val processors: ListBuffer[Processor[R]] = ListBuffer.empty
  var events = Seq.empty[R]

  def extractData(blockJob: BlockJob): Seq[R] = {
    events = (blockJob.txs zip blockJob.receipts).flatMap { item =>
      extractor.extract(item._1, item._2, blockJob.timestamp)
    }
    events
  }

  def process() = {
    processors.foreach(processor => {
      events.foreach(processor.process)
    })
  }

  def addProcessor(p: Processor[R]*) = processors.append(p: _*)

  def getData: Seq[R] = events

}
