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

package org.loopring.lightcone.core.base

import org.loopring.lightcone.proto.{XError, XErrorCode}

final class ErrorException(val error: XError) extends Exception {
  override def getMessage() = s"ErrorException(${error.code}: ${error.message})"
}

object ErrorException {

  def apply(
      code: XErrorCode,
      message: String
    ) = new ErrorException(XError(code, message))
  def apply(error: XError) = new ErrorException(error)
}
