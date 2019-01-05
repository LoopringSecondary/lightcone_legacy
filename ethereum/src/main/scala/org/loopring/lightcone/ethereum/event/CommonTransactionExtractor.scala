package org.loopring.lightcone.ethereum.event

import org.loopring.lightcone.proto.{Transaction, TransactionEvent, TransactionReceipt}

class CommonTransactionExtractor() extends TransactionExtractor {

  def extract(
               tx: Transaction,
               receipt: TransactionReceipt
             ): Seq[TransactionEvent] =
    Seq(
      txToEvent(tx).copy(
        eventType = TransactionEvent.Type.COMMON,
        status = getStatus(receipt.status)
      )
    )
}
