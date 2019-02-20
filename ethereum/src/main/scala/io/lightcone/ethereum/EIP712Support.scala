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
import org.web3j.abi.TypeEncoder
import org.web3j.abi.datatypes.{Address => Web3jAddress, Uint, Int}
import org.web3j.abi.datatypes.generated.Bytes32
import io.lightcone.core._

case class TypeItem(
    name: String,
    `type`: String)
case class Type(typeItems: List[TypeItem])
case class Types(types: Map[String, Type])
case class EIP712TypedData(
    types: Types,
    primaryType: String,
    domain: Map[String, Any],
    message: Map[String, Any])

trait EIP712Support {
  def jsonToTypedData(jsonString: String): Either[ErrorCode, EIP712TypedData]
  def getEIP712Message(typedData: EIP712TypedData): String
}

class DefaultEIP712Support extends EIP712Support {
  import ErrorCode._

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
  def jsonToTypedData(jsonStr: String): Either[ErrorCode, EIP712TypedData] = {
    try {
      val root = parse(jsonStr)

      val typesMap = (root \ "types").extract[Map[String, List[TypeItem]]]
      val types = Types(typesMap.map(kv => (kv._1, Type(kv._2))))
      val primaryType = (root \ "primaryType").asInstanceOf[JString].values
      val domain = (root \ "domain").asInstanceOf[JObject].values
      val message = (root \ "message").asInstanceOf[JObject].values

      val typedData = EIP712TypedData(types, primaryType, domain, message)
      println(s"typedData: $typedData")
      Right(typedData)
    } catch {
      case e: Throwable =>
        Left(ERR_EIP712_INVALID_JSON_DATA)
    }
  }

  def getEIP712Message(typedData: EIP712TypedData): String = {
    val domainHash =
      hashStruct("EIP712Domain", typedData.domain, typedData.types)
    val messageHash =
      hashStruct(typedData.primaryType, typedData.message, typedData.types)

    val messageBuilder = new StringBuilder
    messageBuilder ++= EIP191Header
    messageBuilder ++= domainHash
    messageBuilder ++= messageHash

    Numeric.toHexString(Hash.sha3(messageBuilder.map(_.toByte).toArray))
  }

  private def hashStruct(
      primaryType: String,
      data: Map[String, Any],
      typeDefs: Types
    ): String = {
    val encodedString = encodeData(primaryType, data, typeDefs)
    Numeric.toHexString(Hash.sha3(encodedString.getBytes))
  }

  private def encodeData(
      dataType: String,
      data: Map[String, Any],
      typeDefs: Types
    ): String = {
    val dataTypeHashBytes = hashType(dataType, typeDefs)
    var encodedValues = List[String](Numeric.toHexString(dataTypeHashBytes))

    val dataTypeItems = typeDefs.types(dataType).typeItems
    dataTypeItems.foreach(typeItem => {
      data.get(typeItem.name) match {
        case Some(value) =>
          val stringValue = value match {
            case js: JString => js.values
            case _           => "0x0"
          }

          typeItem.`type` match {
            case "string" | "bytes" =>
              val valueHash =
                Numeric.toHexString(Hash.sha3(stringValue.getBytes))
              encodedValues = valueHash :: encodedValues
            case bytesn if (bytesn.startsWith("bytes")) =>
              val bytesData = new Bytes32(stringValue.getBytes)
              val encodedValue = TypeEncoder.encode(bytesData)
              encodedValues = encodedValue :: encodedValues
            case uintType
                if (uintType.startsWith("uint") || "bool" == uintType) =>
              val bigIntValue = Numeric.toBigInt(stringValue)
              val uintData = new Uint(bigIntValue)
              val encodedValue = TypeEncoder.encode(uintData)
              encodedValues = encodedValue :: encodedValues
            case intType if (intType.startsWith("int")) =>
              val bigIntValue = Numeric.toBigInt(stringValue)
              val intData = new Int(bigIntValue)
              val encodedValue = TypeEncoder.encode(intData)
              encodedValues = encodedValue :: encodedValues
            case "address" =>
              val addressData = new Web3jAddress(stringValue)
              encodedValues = TypeEncoder.encode(addressData) :: encodedValues
            case structType if (typeDefs.types.contains(structType)) =>
              val jValue = value.asInstanceOf[JObject]
              val typeValueEncoded =
                encodeData(structType, jValue.values, typeDefs)
              encodedValues = typeValueEncoded :: encodedValues
            case _ =>
              throw new IllegalArgumentException(
                "unsupport solidity data type: " + typeItem.`type`
              )
          }
        case None =>
          throw new IllegalStateException(
            "can not get value for " + typeItem.name + " in type " + typeItem.`type`
          )
      }
    })

    encodedValues.reverse.mkString
  }

  private def hashType(
      dataType: String,
      types: Types
    ): Array[Byte] = {
    val encodedTypeStr = encodeType(dataType, types)
    Hash.sha3(encodedTypeStr.getBytes)
  }

  private def encodeType(
      dataType: String,
      types: Types
    ) = {
    var typeList = List[JArray]()
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
