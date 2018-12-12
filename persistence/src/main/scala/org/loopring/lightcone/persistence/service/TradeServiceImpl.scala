package org.loopring.lightcone.persistence.service
import com.google.inject.Inject
import com.google.inject.name.Named
import org.loopring.lightcone.persistence.dals.{TradeDal, TradeDalImpl}
import org.loopring.lightcone.proto._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import scala.concurrent.{ExecutionContext, Future}

class TradeServiceImpl @Inject() (
  implicit
  val dbConfig: DatabaseConfig[JdbcProfile],
  @Named("db-execution-context") val ec: ExecutionContext
) extends TradeService {
  val tradeDal: TradeDal = new TradeDalImpl()

  def saveTrade(trade: XTrade): Future[Either[XErrorCode, String]] = tradeDal.saveTrade(trade)

  def getTrades(request: XGetTradesReq): Future[Seq[XTrade]] = tradeDal.getTrades(request)

  def countTrades(request: XGetTradesReq): Future[Int] = tradeDal.countTrades(request)

  def obsolete(height: Long): Future[Unit] = tradeDal.obsolete(height)
}
