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

package org.loopring.lightcone.lib.time

import java.sql.Timestamp

trait TimeProvider {
  def getTimeMillis: Long
  def getTimeSeconds: Long = getTimeMillis / 1000
  def getTimestamp = new Timestamp(getTimeMillis)
}

final class LocalSystemTimeProvider extends TimeProvider {
  def getTimeMillis = System.currentTimeMillis()
}

final class DifferenceAssuredLocalSystemTimeProvider extends TimeProvider {
  private var lastTimestamp = System.currentTimeMillis

  def getTimeMillis = {
    val now = System.currentTimeMillis
    if (now > lastTimestamp) lastTimestamp = now
    else lastTimestamp += 1

    lastTimestamp
  }
}
