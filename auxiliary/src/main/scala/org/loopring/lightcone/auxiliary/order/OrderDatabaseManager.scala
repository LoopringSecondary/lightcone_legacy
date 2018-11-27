package org.loopring.lightcone.auxiliary.order

import org.loopring.lightcone.proto.actors.{ XErrorCode, XOrderState, XRawOrder }
import org.loopring.lightcone.proto.core.XOrderStatus

import scala.concurrent.Future

trait OrderDatabaseManager {

  def validateOrder(xraworder: XRawOrder): Either[XErrorCode, XRawOrder]

  def saveOrder(xraworder: XRawOrder): Future[XRawOrder]

  def getOrdersForRecovery(since: Long, num: Int, owner: Option[String]): Future[Seq[XRawOrder]]

  def updateOrderStateAndStatus(actualState: XOrderState, status: XOrderStatus): Future[Boolean]

  // TODO(litao): design more flexibale order reading APIs
  def getOrder(orderId: String): Future[Option[XRawOrder]]

  def getOrders(orderIds: Seq[String]): Future[Seq[XRawOrder]]

}
