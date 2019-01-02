/*
 * Copyright 2018 Loopring Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.loopring.lightcone.persistence.tables

import org.loopring.lightcone.persistence.base.{enumColumnType, BaseTable}
import org.loopring.lightcone.proto._
import slick.jdbc.MySQLProfile.api._

class OrderStatusMonitorTable(tag: Tag)
    extends BaseTable[OrderStatusMonitor](tag, "T_ORDER_STATUS_MONITOR") {

  def id = monitorType
  def processTime = column[Long]("process_time")
  def monitorType = column[String]("monitor_type", O.PrimaryKey, O.Unique)

  def * =
    (
      monitorType,
      processTime
    ) <> ((OrderStatusMonitor.apply _).tupled, OrderStatusMonitor.unapply)

}
