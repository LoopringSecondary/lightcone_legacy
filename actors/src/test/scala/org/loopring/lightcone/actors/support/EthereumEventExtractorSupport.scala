package org.loopring.lightcone.actors.support

import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.ethereum.event._
trait EthereumEventExtractorSupport {
  my:CommonSpec =>

  implicit val balanceExtractor = new BalanceChangedAddressExtractor
  implicit val allowanceExtractor = new AllowanceChangedAddressExtractor
  implicit val cutoffExtractor = new CutoffEventExtractor
  implicit val ordersCancelledExtractor = new OrdersCancelledEventExtractor
  implicit val tokenBurnRateExtractor = new TokenBurnRateEventExtractor
  implicit val transferExtractor = new TransferEventExtractor
  implicit val ringMinedExtractor = new RingMinedEventExtractor

  implicit val dispatchers = Seq(
    new CutoffEventDispatcher,
    new OrdersCancelledEventDispatcher,
    new TokenBurnRateChangedEventDispatcher,
    new TransferEventDispatcher,
    new OrderFilledEventDispatcher,
    new RingMinedEventDispatcher,
    new BalanceEventDispatcher,
    new AllowanceEventDispatcher
  )

  actors.add(EthereumEventExtractorActor.name,EthereumEventExtractorActor.start)
  actors.add(EthereumBlockSupplementActor.name,EthereumBlockSupplementActor.start)
}
