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

package org.loopring.lightcone.persistence

import org.scalatest.{ BeforeAndAfterAll, FlatSpec }
import scala.concurrent.duration._
import scala.concurrent.Await
import slick.jdbc.meta._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.MySQLProfile.backend.Database

trait TableSpec[T <: base.BaseTable[A], A] extends FlatSpec with BeforeAndAfterAll {
  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true
  var db: Database = _
  val query: TableQuery[T]

  override def beforeAll = {
    println(s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`")
    db = Database.forConfig("db_test")

    val setup = DBIO.seq(
      // query.schema.drop,
      query.schema.create
    )

    Await.result(db.run(setup), 4.second)
  }

  override def afterAll = {
    db.close()
  }

}
