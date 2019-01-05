package org.loopring.lightcone.ethereum.event

import org.loopring.lightcone.proto.{Transaction, TransactionReceipt, TransferEvent ⇒ PTransferEvent}

class TransferEventExtractor extends DataExtractor[PTransferEvent] {

  def extract(tx: Transaction, receipt: TransactionReceipt, blockTime: String): Seq[PTransferEvent] = {


    Seq.empty

  }
}
