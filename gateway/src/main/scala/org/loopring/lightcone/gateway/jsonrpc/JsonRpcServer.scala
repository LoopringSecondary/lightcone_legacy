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

import org.json4s._
import org.json4s.jackson.Serialization
import org.slf4j.LoggerFactory

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class JsonRpcServer(settings: JsonRpcSettings) {

  private[jsonrpc] lazy val logger = LoggerFactory.getLogger(getClass)

  private[jsonrpc] implicit val formats = DefaultFormats

  def handleRequest(json: String)(
    implicit
    ex: ExecutionContext): Future[Option[String]] = {
    handleRequest(parseJson(json))
  }

  //  -32700	解析错误	服务器接收到无效的JSON；服务器解析JSON文本发生错误。
  //  -32600	无效的请求	发送的JSON不是一个有效的请求。
  //  -32601	方法未找到	方法不存在或不可见。
  //  -36602	无效的参数	无效的方法参数。
  //  -36603	内部错误	JSON-RPC内部错误。
  //  -32000到-32099	服务器端错误	保留给具体实现服务器端错误。
  def handleRequest(req: JsonRpcRequest)(
    implicit
    ex: ExecutionContext): Future[Option[String]] = {

    val futureValue = try {

      val request = parseRequest(req)

      val mmOption = settings.findProvider(request.method)

      require(mmOption.isDefined, JsonRpcMethodException)

      JsonRpcProxy.invoke(request, mmOption.get)

    } catch {
      case e: JsonRpcException ⇒
        logger.error(s"${e.code} @ ${e.message}", e)
        Future(JsonRpcResponse(id = e.id, error = Some(e.copy(id = None))))
      case e: Exception ⇒
        logger.error(s"failed @ ${e.getMessage}", e)
        Future(JsonRpcResponse(error = Some(JsonRpcInternalException(e.getMessage))))
    }

    futureValue map { resp ⇒
      Try(Serialization.write(resp)).toOption
    }

  }

  def parseRequest(request: JsonRpcRequest): JsonRpcRequest = {
    parseJValue(Extraction.decompose(request))
  }

  def parseJValue(jValue: JValue): JsonRpcRequest = {

    val idOpt = jValue \\ "id" match {
      case JString(x) if x.nonEmpty ⇒ Some(x.toInt)
      case JInt(x) ⇒ Some(x.toInt)
      case _ ⇒ None
    }

    val mthOpt = jValue \\ "method" match {
      case JString(x) if x.nonEmpty ⇒ Some(x.trim)
      case _ ⇒ None
    }

    val rpcOpt = jValue \\ "jsonrpc" match {
      case JString(x) if x == "2.0" ⇒ Some(x)
      case _ ⇒ None
    }

    require(idOpt.isDefined, JsonRpcInvalidException)

    require(mthOpt.isDefined, JsonRpcMethodException)

    require(rpcOpt.isDefined, JsonRpcInvalidException)

    JsonRpcRequest(id = idOpt.get, jsonrpc = rpcOpt.get, method = mthOpt.get, params = (jValue \\ "params"))

  }

  final def require(requirement: Boolean, error: AbstractJsonRpcException): Unit =
    if (!requirement) throw error

  def parseJson(json: String): JsonRpcRequest = {
    import org.json4s.jackson.JsonMethods._
    parseJValue(parse(json))
  }

}

