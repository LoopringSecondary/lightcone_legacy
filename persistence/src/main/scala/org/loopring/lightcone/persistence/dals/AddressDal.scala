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

import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto.ethereum._
import org.loopring.lightcone.proto.core._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import scala.concurrent._

trait AddresDal
  extends BaseDalImpl[AddressTable, XAddressData] {

  def update(row: XAddressData): Future[Int]
  def update(rows: Seq[XAddressData]): Future[Unit]

  def findAddress(address: String): Future[Option[XAddressData]]
  def deleteAddress(address: String): Future[Int]
  def deleteAddress(addresses: Seq[String]): Future[Int]
}

class AddresDalImpl()(
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    val ec: ExecutionContext
) extends AddresDal {
  val query = TableQuery[AddressTable]

  def update(row: XAddressData): Future[Int] = {
    db.run(query.filter(_.address === row.address).update(row))
  }

  def update(rows: Seq[XAddressData]): Future[Unit] = {
    db.run(DBIO.seq(rows.map(r ⇒ query.filter(_.address === r.address).update(r)): _*))
  }

  def findAddress(address: String): Future[Option[XAddressData]] = {
    db.run(query.filter(_.address === address).result.headOption)
  }
  def deleteAddress(address: String): Future[Int] =
    deleteAddress(Seq(address))

  def deleteAddress(addresses: Seq[String]): Future[Int] =
    db.run(query.filter(_.address.inSet(addresses)).delete)

}
