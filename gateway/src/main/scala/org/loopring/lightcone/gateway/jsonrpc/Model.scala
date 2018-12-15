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

package org.loopring.lightcone.gateway.jsonrpc
import org.json4s.JObject

case class JsonRpcRequest(
    jsonrpc: String,
    method: String,
    params: Option[JObject],
    id: Option[String] = None)

case class JsonRpcError(
    code: Int,
    message: Option[String] = None,
    data: Option[String] = None)

case class JsonRpcResponse(
    jsonrpc: String,
    method: String,
    result: Option[String] = None,
    error: Option[JsonRpcError] = None,
    id: Option[String] = None)
