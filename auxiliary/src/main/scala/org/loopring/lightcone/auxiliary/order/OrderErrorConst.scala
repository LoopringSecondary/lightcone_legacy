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

package org.loopring.lightcone.auxiliary.order

case class ErrorResp(code: String, msg: String)

object OrderErrorConst {
  def UNEXPECTED_ORDER_SUBMIT_REQ =
    ErrorResp(
      "UNEXPECTED_ORDER_SUBMIT_REQ",
      "unexpect submitOrderReq"
    )

  def UNEXPECTED_ORDER_SOFT_CANCEL_REQ =
    ErrorResp(
      "UNEXPECTED_ORDER_SOFT_CANCEL_REQ",
      "unexpect softCancelOrders request"
    )

  def ORDER_IS_EMPTY =
    ErrorResp(
      "ORDER_IS_EMPTY",
      "order request is empty"
    )

  def GENERATE_HASH_FAILED =
    ErrorResp(
      "GENERATE_HASH_FAILED",
      "generate hash failed"
    )

  def FILL_PRICE_FAILED =
    ErrorResp(
      "FILL_PRICE_FAILED",
      "fill price failed"
    )

  def ORDER_EXIST =
    ErrorResp(
      "ORDER_EXIST",
      "order had exist in database"
    )

  def SAVE_ORDER_FAILED =
    ErrorResp(
      "SAVE_ORDER_FAILED",
      "save order failed, please try later"
    )

  def SOFT_CANCEL_SIGN_CHECK_FAILED =
    ErrorResp(
      "SOFT_CANCEL_SIGN_CHECK_FAILED",
      "soft check sign is incorrect"
    )

  def NO_ORDER_WILL_BE_SOFT_CANCELLED =
    ErrorResp(
      "NO_ORDER_WILL_BE_SOFT_CANCELLED",
      "no order will be cancelled by the soft cancel condition"
    )

  def SOFT_CANCEL_FAILED =
    ErrorResp(
      "SOFT_CANCEL_FAILED",
      "soft cancel failed, please try again"
    )
}
