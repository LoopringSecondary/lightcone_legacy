/*

  Copyright 2017 Loopring Project Ltd (Loopring Foundation).

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

*/

package io.lightcone.relayer.ethereum.event
import io.lightcone.ethereum.BlockData
import io.lightcone.ethereum.event._
import io.lightcone.relayer.data.Transaction

trait EventExtractorAlt[T, R] {
  def extractEvents(t:T):Seq[R]
}

trait BlockEventExtractor[R] extends EventExtractorAlt[BlockData, R] {
  def extractEvents(t:BlockData):Seq[R]
}

trait TxEventExtractor[R] extends EventExtractorAlt[Transaction, R] {
  def extractEvents(t:Transaction):Seq[R]
}

class TransferEventExtractor extends TxEventExtractor[TransferEvent] {
  def extractEvents(
    t: Transaction
  ): Seq[TransferEvent] = ???
}

class NonceEventExtractor extends TxEventExtractor[AnyRef] {
  def extractEvents(
                     t: Transaction
                   ): Seq[AnyRef] = ???
}

//会有miner的地址变动等
class BalanceChangedExtractor(transferExtractor: TxEventExtractor[TransferEvent]) extends BlockEventExtractor[AddressBalanceAllowanceUpdatedEvent] {

  def extractEvents(
    t: BlockData
  ): Seq[AddressBalanceAllowanceUpdatedEvent] = ???

}

class AllowanceChangedExtractor(transferExtractor: TxEventExtractor[TransferEvent]) extends BlockEventExtractor[AddressAllowanceUpdatedEvent] {

  def extractEvents(
    t: BlockData
  ): Seq[AddressAllowanceUpdatedEvent] = ???

}

object Main {
  val transferExtractor = new TransferEventExtractor()

  val extractors = Seq(
    new BalanceChangedExtractor(transferExtractor),
    new AllowanceChangedExtractor(transferExtractor)
  )

  def extractEvents(block: BlockData):Seq[AnyRef] = {
    extractors.map(_.extractEvents(BlockData()))
  }

  def main(args: Array[String]): Unit = {

    val events = extractEvents(BlockData())
    events

  }
}





