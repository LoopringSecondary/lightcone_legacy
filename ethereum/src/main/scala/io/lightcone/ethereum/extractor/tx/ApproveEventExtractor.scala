package io.lightcone.ethereum.extractor.tx

import io.lightcone.ethereum.extractor.{EventExtractor, TransactionData}

import scala.concurrent.Future

class ApproveEventExtractor extends EventExtractor[TransactionData, AnyRef] {


  override def extractEvents(source: TransactionData): Future[Seq[AnyRef]] = ???



    def extractApproveEvents()



}
