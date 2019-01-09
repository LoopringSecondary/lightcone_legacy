package org.loopring.lightcone.actors.ethereum.event

import org.loopring.lightcone.ethereum.event.{CutOffEventExtractor, DataExtractor, OnlineOrderExtractor}
import org.loopring.lightcone.proto._

class CutOffEventExtractorWrapped() extends DataExtractorWrapped[CutoffEvent] {
   val extractor: DataExtractor[CutoffEvent] = new CutOffEventExtractor()

}

class OnlineOrderExtractorWrapped() extends DataExtractorWrapped[RawOrder]{
   val extractor: DataExtractor[RawOrder] = new OnlineOrderExtractor()
}

class OrdersCancelledEventExtractorWrapped() extends DataExtractor[OrdersCancelledEvent] {

   def extract(tx: Transaction, receipt: TransactionReceipt, blockTime: String): Seq[OrdersCancelledEvent] = {

   }
}
