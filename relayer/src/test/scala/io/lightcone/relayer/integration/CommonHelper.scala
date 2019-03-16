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

package io.lightcone.relayer.integration
import io.lightcone.relayer.integration.helper.{DbHelper, MockHelper}
import org.scalatest.{BeforeAndAfterEach, Matchers}

trait CommonHelper
    extends MockHelper
    with DbHelper
    with Matchers
    with RpcHelper
    with OrderHelper
    with BeforeAndAfterEach {

  //保证每次都重置ethmock和数据库，
  //当需要不同的重置条件时，需要覆盖该方法
  override protected def beforeEach(): Unit = {
    setDefaultEthExpects()
    prepareDbModule(dbModule)
    prepareMetadata(dbModule, metadataManager)(timeout, timeProvider)
    super.beforeEach()
  }

  def prepareMarkets() = {}

}
