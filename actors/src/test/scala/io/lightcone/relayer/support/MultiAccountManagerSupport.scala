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

import io.lightcone.relayer.actors._
import io.lightcone.relayer.validator.{
  MessageValidationActor,
  MultiAccountManagerMessageValidator
}

trait MultiAccountManagerSupport
  extends DatabaseModuleSupport
  with EthereumSupport {
  my: CommonSpec =>
  actors.add(MultiAccountManagerActor.name, MultiAccountManagerActor.start)

  actors.add(
    MultiAccountManagerMessageValidator.name,
    MessageValidationActor(
      new MultiAccountManagerMessageValidator(),
      MultiAccountManagerActor.name,
      MultiAccountManagerMessageValidator.name))
}
