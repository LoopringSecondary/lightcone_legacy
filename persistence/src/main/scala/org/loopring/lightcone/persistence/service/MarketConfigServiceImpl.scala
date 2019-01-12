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

package org.loopring.lightcone.persistence.service

import com.google.inject.Inject
import org.loopring.lightcone.persistence.dals._
import org.loopring.lightcone.proto._
import scala.concurrent.{ExecutionContext, Future}

class MarketConfigServiceImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    marketConfigDal: MarketConfigDal)
    extends MarketConfigService {

  def saveConfigs(configs: Seq[MarketConfig]): Future[Int] =
    marketConfigDal.saveConfigs(configs)

  def getAllConfigs(): Future[Seq[MarketConfig]] =
    marketConfigDal.getAllConfigs()

  def updateConfig(config: MarketConfig): Future[Int] =
    marketConfigDal.updateConfig(config)
}
