package org.loopring.lightcone.persistence.service

import org.loopring.lightcone.persistence.dals.TradeDal
import org.loopring.lightcone.proto.{XErrorCode, XGetTradesReq, XTrade}
import scala.concurrent.Future

trait TradeService {

  val tradeDal: TradeDal
  def saveTrade(trade: XTrade): Future[Either[XErrorCode, String]]
  def getTrades(request: XGetTradesReq): Future[Seq[XTrade]]
  def countTrades(request: XGetTradesReq): Future[Int]
  def obsolete(height: Long): Future[Unit]
}
