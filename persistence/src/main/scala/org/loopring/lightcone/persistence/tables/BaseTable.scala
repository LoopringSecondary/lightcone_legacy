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

package org.loopring.lightcone.persistence.table

import slick.jdbc.MySQLProfile.api._
import slick.ast.ColumnOption
import com.google.protobuf.ByteString

abstract class BaseTable[T](tag: Tag, name: String)
  extends Table[T](tag, name) {

  def columnHash(name: String, options: ColumnOption[String]*) =
    column[String](name, (Seq(O.SqlType("VARCHAR(66)")) ++ options): _*)

  def columnAddress(name: String, options: ColumnOption[String]*) =
    column[String](name, (Seq(O.SqlType("VARCHAR(42)")) ++ options): _*)

  def columnAmount(name: String, options: ColumnOption[ByteString]*) =
    column[ByteString](name, options: _*)

  implicit val boolColumnType: BaseColumnType[ByteString] =
    MappedColumnType.base[ByteString, Array[Byte]](
      bs ⇒ bs.toByteArray(),
      bytes ⇒ ByteString.copyFrom(bytes)
    )
}
