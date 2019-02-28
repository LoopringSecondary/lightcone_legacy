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

package io.lightcone.relayer.validator

import io.lightcone.core.ErrorCode.ERR_INVALID_ARGUMENT
import io.lightcone.core.{ErrorCode, ErrorException}
import io.lightcone.lib.Address
import io.lightcone.persistence.{CursorPaging, Paging}
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

object MessageValidator {

  def normalizeAddress(address: String) = {
    if (address.nonEmpty) Address.normalize(address) else ""
  }

  def normalizeHash(hash: String) = {
    if (hash.nonEmpty) hash.toLowerCase else ""
  }

  def isValidNumber(str: String) = {
    try {
      str.toLong
      true
    } catch {
      case _: Throwable => false
    }
  }

  def getValidPaging(
      paging: Option[Paging]
    )(
      implicit
      pageConfig: PageConfig
    ) = {
    paging match {
      case Some(s) if s.size > pageConfig.maxItemsPerPage =>
        throw ErrorException(
          ERR_INVALID_ARGUMENT,
          s"Parameter size of paging is larger than ${pageConfig.maxItemsPerPage}"
        )

      case Some(s) if s.skip < 0 =>
        throw ErrorException(
          ERR_INVALID_ARGUMENT,
          s"Invalid parameter skip of paging:${s.skip}"
        )

      case Some(s) => paging

      case None =>
        Some(Paging(size = pageConfig.defaultItemsPerPage))
    }
  }

  def getValidCursorPaging(
      paging: Option[CursorPaging]
    )(
      implicit
      pageConfig: PageConfig
    ) = {
    paging match {
      case Some(s) if s.size > pageConfig.maxItemsPerPage =>
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          s"Parameter size of paging is larger than ${pageConfig.maxItemsPerPage}"
        )

      case Some(s) if s.cursor < 0 =>
        throw ErrorException(
          ErrorCode.ERR_INVALID_ARGUMENT,
          s"Invalid parameter cursor of paging:${s.cursor}"
        )

      case Some(s) => paging

      case None =>
        Some(CursorPaging(size = pageConfig.defaultItemsPerPage))
    }
  }
}

case class PageConfig(
    defaultItemsPerPage: Int,
    maxItemsPerPage: Int) {}
