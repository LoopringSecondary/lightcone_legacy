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

import org.loopring.lightcone.persistence.base.BaseDalImpl
import org.loopring.lightcone.persistence.tables.TokenConfigTable
import org.loopring.lightcone.proto.TokenConfig
import scala.concurrent.Future

trait TokenConfigDal extends BaseDalImpl[TokenConfigTable, TokenConfig] {
  def saveConfigs(configs: Seq[TokenConfig]): Future[Int]
  def getAllConfigs(): Future[Seq[TokenConfig]]
  def updateConfig(config: TokenConfig): Future[Int]
}
