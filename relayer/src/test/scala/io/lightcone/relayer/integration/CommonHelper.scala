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
import io.lightcone.core.testing.OrderHelper
import io.lightcone.relayer.integration.helper._
import org.scalatest.{BeforeAndAfterAll, Matchers}

abstract class CommonHelper
    extends MockHelper
    with DbHelper
    with Matchers
    with RpcHelper
    with BeforeAndAfterAll
    with OrderHelper {
  override def beforeAll(): Unit = {
    info(s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`")
    super.beforeAll()
  }

}
