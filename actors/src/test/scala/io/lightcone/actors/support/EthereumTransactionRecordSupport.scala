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

package io.lightcone.actors.support

import com.typesafe.config.{Config, ConfigFactory}
import io.lightcone.actors.DatabaseConfigManager
import io.lightcone.actors.core._
import io.lightcone.actors.validator.{
  MessageValidationActor,
  TransactionRecordMessageValidator
}

trait EthereumTransactionRecordSupport extends DatabaseModuleSupport {
  my: CommonSpec =>

  override def afterAll: Unit = {
    dcm.close()
    super.afterAll
  }

  val config1 = ConfigFactory
    .parseString(transactionRecordConfigStr)
    .withFallback(ConfigFactory.load())

  val dcm = new DatabaseConfigManager(config1)

  actors.add(
    TransactionRecordActor.name,
    TransactionRecordActor
      .start(
        system,
        config1,
        ec,
        timeProvider,
        timeout,
        actors,
        dbModule,
        dcm,
        true
      )
  )

  actors.add(
    TransactionRecordMessageValidator.name,
    MessageValidationActor(
      new TransactionRecordMessageValidator(),
      TransactionRecordActor.name,
      TransactionRecordMessageValidator.name
    )
  )
}
