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

package org.loopring.lightcone.persistence.database.base

import slick.jdbc.MySQLProfile.api._
import slick.lifted.Tag

abstract class BaseTable[T](tag: Tag, name: String)
  extends Table[T](tag, "LC_" + name) {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def createdAt = column[Long]("created_at", O.Default(System.currentTimeMillis))
  def updatedAt = column[Long]("updated_at", O.Default(System.currentTimeMillis))
}
