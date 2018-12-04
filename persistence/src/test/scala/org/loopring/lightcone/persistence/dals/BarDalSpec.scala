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

import scala.concurrent.duration._
import scala.concurrent.Await
import slick.jdbc.meta._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import org.loopring.lightcone.proto.persistence.Bar
import com.google.protobuf.ByteString
import scala.concurrent._
import org.loopring.lightcone.persistence.base._

class BarDalSpec extends DalSpec[BarDal] {
  val dal = new BarDalImpl()

  "BarsDal" must "create table and index correctly" in {
    var bar = Bar(id = 100, hash = "hash", a = "b", b = "c", c = ByteString.copyFrom("d".getBytes), d = 12L)
    Await.result(dal.insert(bar), 5.second)

    bar = Bar(id = 100, hash = "hash2", a = "b", b = "c", c = ByteString.copyFrom("d".getBytes), d = 12L)
    Await.result(dal.insert(bar), 5.second)
  }
}
