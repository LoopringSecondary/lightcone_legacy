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

package org.loopring.lightcone.actors.validator

import scala.concurrent.Future

// Example:
// class MyMessageValidator extends MessageValidator {
//   def validate = {
//     case x: SomeMessageA => // pass as-is
//     case x: SomeMessageB => x // pass as-is
//     case x: SomeMessageC => x.copy(field=value) // modify
//     // unhandled messages are passed as-is
//
//

// Owner: Daniel
trait MessageValidator {
  // Throw exceptions if validation failed.
  def validate: PartialFunction[Any, Future[Any]]
}
