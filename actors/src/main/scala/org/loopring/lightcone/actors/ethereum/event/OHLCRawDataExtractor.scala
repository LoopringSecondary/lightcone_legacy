package org.loopring.lightcone.actors.ethereum.event

import com.google.inject.Inject
import org.loopring.lightcone.proto.{OrderFilledEvent, RawBlockData}

import scala.concurrent.{ExecutionContext, Future}

class OHLCRawDataExtractor @Inject()(
                                      implicit
                                      extractor: OrderFillEventExtractor,
                                      val ec: ExecutionContext)
  extends EventExtractor[OrderFilledEvent]{

   def extract(block: RawBlockData): Future[Seq[OrderFilledEvent]] = {

     extractor.extract(block)
   }

}
