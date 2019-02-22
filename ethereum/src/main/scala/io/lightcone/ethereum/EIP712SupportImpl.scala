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
import org.web3j.abi.datatypes.{Address => Web3jAddress, Uint, Int, Bool}
import org.web3j.abi.datatypes.generated.Bytes32
import io.lightcone.core._

class DefaultEIP712Support extends EIP712Support {
  import ErrorCode._

  implicit val formats = DefaultFormats

  val EIP191HeaderHex = "1901"

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

  def jsonToTypedData(jsonStr: String) = {
    try {
      val root = parse(jsonStr)
      val typesMap = (root \ "types").extract[Map[String, List[TypeItem]]]
      val types = Types(typesMap.map(kv => (kv._1, Type(kv._1, kv._2))))
      val primaryType = (root \ "primaryType").asInstanceOf[JString].values
      val domain = (root \ "domain").asInstanceOf[JObject].values
      val message = (root \ "message").asInstanceOf[JObject].values

      EIP712TypedData(types, primaryType, domain, message)
    } catch {
      case e: Throwable =>
        throw ErrorException(ERR_EIP712_INVALID_JSON_DATA, e.getMessage)
    }
  }

  def getEIP712Message(typedData: EIP712TypedData): String = {
    val domainHash =
      hashStruct("EIP712Domain", typedData.domain, typedData.types)

    val messageHash =
      hashStruct(typedData.primaryType, typedData.message, typedData.types)
    val source = List[String](EIP191HeaderHex, domainHash, messageHash)
      .map(s => Numeric.cleanHexPrefix(s))
      .mkString

    Hash.sha3(source)
  }

  private def hashStruct(
      primaryType: String,
      data: Map[String, Any],
      typeDefs: Types
    ): String = {
    val encodedString = encodeData(primaryType, data, typeDefs)
    Hash.sha3(encodedString)
  }

  private def encodeData(
      dataType: String,
      data: Map[String, Any],
      typeDefs: Types
    ): String = {
    val dataTypeHash = Numeric.toHexString(hashType(dataType, typeDefs))

    var encodedValues = List[String](dataTypeHash)

    val dataTypeItems = typeDefs.types(dataType).typeItems
    dataTypeItems.foreach(typeItem => {
      data.get(typeItem.name) match {
        case Some(value) =>
          val stringValue = value match {
            case s: String => if (s.length > 0) s else "0x0"
            case _         => "0x0"
          }

          typeItem.`type` match {

            case "string" =>
              val valueHash =
                Numeric.toHexString(Hash.sha3(stringValue.getBytes))
              encodedValues = valueHash :: encodedValues

            case "bytes" =>
              encodedValues = Hash.sha3(stringValue) :: encodedValues

            case bytesn if (bytesn.startsWith("bytes") && bytesn.length > 5) =>
              val valueBigInt = Numeric.toBigInt(stringValue)
              val valueBytes32 = Numeric.toBytesPadded(valueBigInt, 32)
              val bytesData = new Bytes32(valueBytes32)
              val encodedValue = TypeEncoder.encode(bytesData)
              encodedValues = encodedValue :: encodedValues

            case uintType if (uintType.startsWith("uint")) =>
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

            case "bool" =>
              val boolValue = value.asInstanceOf[Boolean]
              val boolData = new Bool(boolValue)
              encodedValues = TypeEncoder.encode(boolData) :: encodedValues

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
    encodedValues.reverse.map(s => Numeric.cleanHexPrefix(s)).mkString
  }

  private def hashType(
      dataType: String,
      allTypes: Types
    ): Array[Byte] = {
    val encodedTypeStr = encodeType(dataType, allTypes)
    Hash.sha3(encodedTypeStr.getBytes)
  }

  private def encodeType(
      dataType: String,
      allTypes: Types
    ) = {
    val deps = findTypeDependencies(dataType, allTypes, List[Type]())
    val depsSorted = deps.sortBy(t => t.name)

    depsSorted
      .map(t => {
        t.name + "(" + t.typeItems
          .map(item => {
            item.`type` + " " + item.name
          })
          .mkString(",") + ")"
      })
      .mkString
  }

  private def findTypeDependencies(
      targetType: String,
      allTypes: Types,
      results: List[Type]
    ): List[Type] =
    allTypes.types.get(targetType) match {
      case Some(typeDef) =>
        typeDef.typeItems
          .map(item => {
            val typeDeps =
              findTypeDependencies(item.`type`, allTypes, typeDef :: results)
            typeDef :: typeDeps
          })
          .flatten
          .distinct
      case None => results
    }

}
