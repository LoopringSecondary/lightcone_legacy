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
import org.json4s.native.JsonMethods._
import org.web3j.utils.Numeric
import org.web3j.crypto.Hash
import io.lightcone.core._

trait EIP712Support {
  def getEIP712Message(typedDataJson: String): Either[ErrorCode, String]
}

class DefaultEIP712Support extends EIP712Support {
  implicit val formats = DefaultFormats

  val EIP191Header = "\u0019\u0001"

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
  def getEIP712Message(typedDataJson: String): Either[ErrorCode, String] = {
    val json = parse(typedDataJson)

    val messageBuilder = new StringBuilder
    messageBuilder ++= EIP191Header

    val domain = json \ "domain"
    val types = json \ "types"
    val eip712DomainType = types \ "EIP712Domain"
    val domainHash =
      hashStruct(eip712DomainType.asInstanceOf[JArray], domain, types)
    messageBuilder ++= domainHash

    println(s"eip712DomainType: ${eip712DomainType}")

    val primaryTypeObj = (json \ "primaryType")
    println(s"primaryTypeObj: $primaryTypeObj")
    val primaryTypeName = primaryTypeObj.asInstanceOf[JString].values
    val primaryType = types \ primaryTypeName
    val message = json \ "message"
    val typesHash = hashStruct(primaryType.asInstanceOf[JArray], message, types)
    messageBuilder ++= typesHash
    println(s"primaryType: $primaryType")

    Right(Numeric.toHexString(Hash.sha3(messageBuilder.map(_.toByte).toArray)))
  }

  private def hashStruct(
      dataTypeArray: JArray,
      data: JValue,
      types: JValue
    ): String = {
    val encodedString = encodeData(dataTypeArray, data, types)
    Numeric.toHexString(Hash.sha3(encodedString.getBytes))
  }

  private def encodeData(
      dataTypeArray: JArray,
      data: JValue,
      types: JValue
    ): String = {
    var encodedTypes = List("Bytes32")

    var encodedValues = List()

    ""
  }

  private def hashType(
      primaryTypeName: String,
      types: JValue
    ) = {
    val encodedTypeStr = encodeType(primaryTypeName, types)
    Numeric.toHexString(Hash.sha3(encodedTypeStr.getBytes))
  }

  private def encodeType(
      primaryTypeName: String,
      types: JValue
    ) = {
    ""
  }

  private def findTypeDependencies(
      targetType: JValue,
      types: JValue,
      results: Array[JValue]
    ) = {
    ""
    // targetType match {
    //   case t: JNothing              =>
    //   case t if results.contains(t) =>
    //   case t =>
    //     val fieldTypes = t.foldField(List(): List[JValue])((l, t) => t :: l)
    //     fieldTypes
    //       .map(
    //         fieldType => findTypeDependencies(fieldType, types, results)
    //       )
    //       .flatten
    //       .distinct
    //       .toArray
    // }
  }

}
