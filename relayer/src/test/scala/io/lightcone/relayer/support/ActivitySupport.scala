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

package io.lightcone.relayer.support

import com.typesafe.config._
import io.lightcone.relayer._
import io.lightcone.relayer.actors._
import io.lightcone.relayer.validator._

trait ActivitySupport extends DatabaseModuleSupport {
  me: CommonSpec =>

  override def afterAll: Unit = {
    dcm.close()
    super.afterAll
  }

  val config1 = ConfigFactory
    .parseString(activityConfigStr)
    .withFallback(ConfigFactory.load())

  val dcm = new DatabaseConfigManager(config1)

  actors.add(
    ActivityActor.name,
    ActivityActor
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
    ActivityValidator.name,
    MessageValidationActor(
      new ActivityValidator(),
      ActivityActor.name,
      ActivityValidator.name
    )
  )
}
