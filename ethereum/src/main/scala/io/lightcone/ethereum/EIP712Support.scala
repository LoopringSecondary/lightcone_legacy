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

import org.json4s._
import org.json4s.JsonAST.JNothing
import org.json4s.native.JsonMethods._
import org.web3j.utils.Numeric
import org.web3j.crypto.Hash

trait EIP712Support {
  def getEIP712Message(typedDataJson: String): String
}

object DefaultEIP712Support extends EIP712Support {
  implicit val formats = DefaultFormats

  val Eip191Header = "\u0019\u0001"

  /* TYPED_MESSAGE_SCHEMA:
   * """
   * {
   *   type: "object",
   *   properties: {
   *     types: {
   *       type: "object",
   *       additionalProperties: {
   *         type: "array",
   *         items: {
   *           type: "object",
   *           properties: {
   *             name: {type: "string"},
   *             type: {type: "string"},
   *           },
   *           required: ["name", "type"],
   *         },
   *       },
   *     },
   *     primaryType: {type: "string"},
   *     domain: {type: "object"},
   *     message: {type: "object"},
   *   },
   *   required: ["types", "primaryType", "domain", "message"],
   * }
   * """
   */
  def getEIP712Message(typedDataJson: String): String = {
    val json = parse(typedDataJson)

    val messageBuilder = new StringBuilder
    messageBuilder ++= Eip191Header

    val domain = json \ "domain"
    val types = json \ "types"
    val eip712DomainType = types \ "EIP712Domain"
    val domainHash = hashStruct(eip712DomainType, domain, types)
    messageBuilder ++= domainHash

    val primaryTypeName = (json \ "primaryType").extract[String]
    val primaryType = json \ primaryTypeName
    val message = json \ "message"
    val typesHash = hashStruct(primaryType, message, types)
    messageBuilder ++= typesHash

    Numeric.toHexString(Hash.sha3(messageBuilder.map(_.toByte).toArray))
  }

  private def hashStruct(
      primaryType: JValue,
      data: JValue,
      types: JValue
    ): String = {
    val encodedString = encodeData(primaryType, data, types)
    Numeric.toHexString(Hash.sha3(encodedString.getBytes))
  }

  private def encodeData(
      primaryType: JValue,
      data: JValue,
      types: JValue
    ): String = {
    var encodedTypes = List("Bytes32")
    var encodedValues = List()

    ???
  }

  private def hashType(
      primaryTypeName: String,
      types: JValue
    ) = {
    Numeric.toHexString(Hash.sha3(encodeType(primaryTypeName, types)))
  }

  private def encodeType(
      primaryTypeName: String,
      types: JValue
    ) = {
    ???
  }

  private def findTypeDependencies(
      targetType: JValue,
      types: JValue,
      results: Array[JValue]
    ) {
    targetType match {
      case t: JNothing              => results
      case t if results.contains(t) => results
      case t =>
        val fieldTypes = t.foldField(List(): List[JValue])((l, t) => t :: l)
        fieldTypes
          .map(
            fieldType => findTypeDependencies(fieldType, types, results)
          )
          .flatten
          .distinct
          .toArray
    }
  }

}
