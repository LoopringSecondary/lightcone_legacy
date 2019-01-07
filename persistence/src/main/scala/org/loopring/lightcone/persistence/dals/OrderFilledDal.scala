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

package org.loopring.lightcone.persistence.dals

import com.typesafe.config.Config
import org.loopring.lightcone.persistence.base._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import scala.concurrent._
import org.loopring.lightcone.persistence.tables.OrderFilledBaseTable

class OrderFilledDalInit(
  )(
    implicit val dbConfig: DatabaseConfig[JdbcProfile],
    val config: Config,
    val ec: ExecutionContext)
    extends BaseSeparateDal {
  val tableSeparate = config.getInt("separate.orderFilled")

  val tables = (0 until tableSeparate).toList.map { num =>
    val clazz = new OrderFilledBaseTable(num)
    TableQuery[clazz.OrderFilledTable]
  }

  def createTables(): Future[Any] = {
    Future(
      tables.map { p =>
        db.run(p.schema.create)
      }
    )
  }

  def dropTables(): Future[Any] = {
    Future(
      tables.map { p =>
        db.run(p.schema.drop)
      }
    )
  }
}

class OrderFilledDalImpl(tableIndex: Int) {
  base: OrderFilledDalInit =>
  val clazz = new OrderFilledBaseTable(tableIndex)
  val query = TableQuery[clazz.OrderFilledTable]

  def get(): Unit = {
    db.run(query.take(1).result)
  }
}
