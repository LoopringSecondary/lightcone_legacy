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

package io.lightcone.ethereum

private[ethereum] final case class TypeItem(
  name: String, `type`: String)

private[ethereum] final case class Type(
  name: String,
  typeItems: List[TypeItem])

private[ethereum] final case class Types(types: Map[String, Type])

final case class EIP712TypedData(
  types: Types,
  primaryType: String,
  domain: Map[String, Any],
  message: Map[String, Any])

trait EIP712Support {
  def jsonToTypedData(jsonString: String): EIP712TypedData

  def getEIP712Message(typedData: EIP712TypedData): String
}
