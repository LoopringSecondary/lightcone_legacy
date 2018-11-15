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

package org.loopring.lightcone.lib

import java.sql.Timestamp

import org.joda.time.Period

package object time {
  implicit class RichSqlTimestamp(timestamp: Timestamp) {
    def +(millis: Long) = new Timestamp(timestamp.getTime + millis)
    def -(millis: Long) = new Timestamp(timestamp.getTime - millis)

    def +(p: Period) = new Timestamp(timestamp.getTime + p.getMillis)
    def -(p: Period) = new Timestamp(timestamp.getTime - p.getMillis)
  }
}
