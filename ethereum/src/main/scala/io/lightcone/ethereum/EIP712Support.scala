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
  import ErrorCode._

  implicit val formats = DefaultFormats

  val EIP191Header = "\u0019\u0001"

  case class TypeItem(
      name: String,
      `type`: String)
  case class SingleTypeDefinition(typeItems: List[TypeItem])
  case class TypeDefinitions(types: Map[String, SingleTypeDefinition])
  case class EIP712TypedData(
      types: TypeDefinitions,
      primaryType: String,
      domain: Any,
      message: Any)

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
    def isJArray(jsonData: JValue) =
      jsonData match {
        case jArray: JArray => true
        case _              => false
      }

    def isJString(jsonData: JValue) =
      jsonData match {
        case jString: JString => true
        case _                => false
      }

    val json = parse(typedDataJson)

    // val eipObj = json.extract[EIP712TypedData]
    // println(s"eipObj: $eipObj")

    val domain = json \ "domain"
    val types = json \ "types"

    val typesObject = types.extract[TypeDefinitions]
    println(s"typesObject: $typesObject")

    val eip712DomainType = types \ "EIP712Domain"

    val domainTypeObject = eip712DomainType.extract[SingleTypeDefinition]
    println(s"domainTypeObject: $domainTypeObject")

    val primaryTypeObj = (json \ "primaryType")
    val primaryTypeName = primaryTypeObj.asInstanceOf[JString].values
    val primaryType = types \ primaryTypeName

    if (isJString(primaryTypeObj) && isJArray(eip712DomainType) && isJArray(
          primaryType
        )) {
      val message = json \ "message"
      val domainHash =
        hashStruct(eip712DomainType.asInstanceOf[JArray], domain, types)
      val typesHash =
        hashStruct(primaryType.asInstanceOf[JArray], message, types)

      val messageBuilder = new StringBuilder
      messageBuilder ++= EIP191Header
      messageBuilder ++= domainHash
      messageBuilder ++= typesHash

      Right(
        Numeric.toHexString(Hash.sha3(messageBuilder.map(_.toByte).toArray))
      )
    } else {
      Left(ERR_EIP712_INVALID_JSON_DATA)
    }
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
    var encodedTypes = List[String]("Bytes32")
    val dataTypeHashBytes = hashType(dataTypeArray, types)
    var encodedValues = List[Array[Byte]](dataTypeHashBytes)

    val topTypeDefs = types.values
    val typeItems = dataTypeArray.values
    val valuesMap = data.values
    println(s"topTypeDefs: $topTypeDefs")
    println(s"typeItems: $typeItems")

    // typeItems.foreach(typeItem => {
    //   val typeItemName = typeItem("name")
    //   val typeItemType = typeItem("type").asInstanceOf[JString].values
    //   valuesMap.get(typeItemName) match {
    //     case Some(value) =>
    //       typeItemType match {
    //         case "string" | "bytes" =>
    //           encodedTypes = "bytes32" :: encodedTypes
    //           val valueHash = Hash.sha3(value.asInstanceOf[String].getBytes)
    //           encodedValues = valueHash :: encodedValues
    //         case structType if (topTypeDefs.contains(typeItemType)) =>
    //           encodedTypes = "bytes32" :: encodedTypes
    //           val itemTypeArray = topTypeDefs(typeItemType).asInstanceOf[JArray]

    //           val typeValueEncoded = encodeData()
    //       }
    //     case None => // doNothing.
    //   }
    // })

    ""
  }

  private def hashType(
      dataTypeArray: JArray,
      types: JValue
    ): Array[Byte] = {
    val encodedTypeStr = encodeType(dataTypeArray, types)
    Hash.sha3(encodedTypeStr.getBytes)
  }

  private def encodeType(
      dataTypeArray: JArray,
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
