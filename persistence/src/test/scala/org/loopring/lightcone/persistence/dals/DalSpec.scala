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
import org.scalatest.{ BeforeAndAfterAll, FlatSpec }
import scala.concurrent.duration._
import scala.concurrent._
import slick.jdbc.meta._
import slick.basic._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile

trait DalSpec[D <: BaseDal[_, _]] extends FlatSpec with BeforeAndAfterAll {
  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true
  implicit val ec = ExecutionContext.global
  implicit var dbConfig = DatabaseConfig.forConfig[JdbcProfile]("db_test")
  val dal: D

  override def beforeAll = {
    println(s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`")
    //TODO du:test
    //Await.result(dal.dropTable(), 5.second)
    //Await.result(dal.createTable(), 5.second)
  }

  override def afterAll = {
    dbConfig.db.close()
  }
}
