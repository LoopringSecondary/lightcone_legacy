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

package org.loopring.lightcone.actors.ethcube

import akka.actor._
import akka.http.scaladsl._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.pattern.pipe
import akka.stream._
import akka.stream.scaladsl._
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s._
import org.json4s.native.JsonMethods.parse
import org.loopring.lightcone.proto.ethrpc._
import scalapb.json4s.JsonFormat

import scala.concurrent._
import scala.util._

private[actors] class HttpConnector(node: XEthereumProxySettings.XNode)(
    implicit
    val mat: ActorMaterializer
) extends Actor
  with ActorLogging
  with Json4sSupport {

  import context.dispatcher

  implicit val serialization = jackson.Serialization //.formats(NoTypeHints)
  implicit val system: ActorSystem = context.system
  implicit val formats = org.json4s.native.Serialization.formats(NoTypeHints) + new EmptyValueSerializer

  val DEBUG_TIMEOUT_STR = "5s"
  val DEBUG_TRACER = "callTracer"
  val ETH_CALL = "eth_call"

  val emptyError = XEthResError(code = 500, error = "result is empty")

  private val poolClientFlow: Flow[(HttpRequest, Promise[HttpResponse]), (Try[HttpResponse], Promise[HttpResponse]), Http.HostConnectionPool] = {
    Http().cachedHostConnectionPool[Promise[HttpResponse]](
      host = node.host,
      port = node.port
    )
  }

  log.info(s"connecting Ethereum at ${node.host}:${node.port}")

  private val queue: SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] =
    Source
      .queue[(HttpRequest, Promise[HttpResponse])](
        100,
        OverflowStrategy.backpressure
      )
      .via(poolClientFlow)
      .toMat(Sink.foreach({
        case (Success(resp), p) ⇒ p.success(resp)
        case (Failure(e), p)    ⇒ p.failure(e)
      }))(Keep.left)
      .run()(mat)

  private def request(request: HttpRequest): Future[HttpResponse] = {
    val responsePromise = Promise[HttpResponse]()
    queue.offer(request -> responsePromise).flatMap {
      case QueueOfferResult.Enqueued ⇒
        responsePromise.future
      case QueueOfferResult.Dropped ⇒
        Future.failed(new RuntimeException("Queue overflowed."))
      case QueueOfferResult.Failure(ex) ⇒
        Future.failed(ex)
      case QueueOfferResult.QueueClosed ⇒
        Future.failed(new RuntimeException("Queue closed."))
    }
  }

  private def post(json: String): Future[String] = {
    post(HttpEntity(ContentTypes.`application/json`, json))
  }

  private def post(entity: RequestEntity): Future[String] = {
    for {
      httpResp ← request(
        HttpRequest(method = HttpMethods.POST, entity = entity)
      )
      jsonStr ← httpResp.entity.dataBytes.map(_.utf8String).runReduce(_ + _)
    } yield jsonStr
  }

  private def sendMessage(method: String)(params: Seq[Any]): Future[String] = {
    val jsonRpc = JsonRpcReqWrapped(
      id = Random.nextInt(100),
      jsonrpc = "2.0",
      method = method,
      params = params
    )
    log.info(s"reqeust: ${org.json4s.native.Serialization.write(jsonRpc)}")

    for {
      entity ← Marshal(jsonRpc).to[RequestEntity]
      jsonStr ← post(entity)
      _ = log.info(s"response: $jsonStr")
    } yield jsonStr

  }

  private def toResponseWrapped: PartialFunction[String, JsonRpcResWrapped] = {
    case json: String ⇒ parse(json).extract[JsonRpcResWrapped]
  }

  private def checkResponseWrapped: PartialFunction[JsonRpcResWrapped, Boolean] = {
    case res: JsonRpcResWrapped ⇒ res.result.toString.isEmpty
  }

  private def hex2BigInt(s: String) = BigInt(s.replace("0x", ""), 16)

  def receive: Receive = {
    case req: XJsonRpcReq ⇒ post(req.json).map(XJsonRpcRes(_)) pipeTo sender
    case _: XEthBlockNumberReq ⇒
      sendMessage("eth_blockNumber") {
        Seq.empty
      } map { json ⇒
        (checkResponseWrapped compose toResponseWrapped)(json) match {
          case true ⇒
            XEthBlockNumberRes().withJsonrpc("2.0")
              .withError(emptyError)
          case _ ⇒ JsonFormat.fromJsonString[XEthBlockNumberRes](json)
        }
      } pipeTo sender

    case r: XEthGetBalanceReq ⇒
      sendMessage("eth_getBalance") {
        Seq(r.address, r.tag)
      } map { json ⇒
        (checkResponseWrapped compose toResponseWrapped)(json) match {
          case true ⇒
            XEthGetBalanceRes().withJsonrpc("2.0")
              .withError(emptyError)
          case _ ⇒ JsonFormat.fromJsonString[XEthGetBalanceRes](json)

        }
      } pipeTo sender

    case r: XGetTransactionByHashReq ⇒
      sendMessage("eth_getTransactionByHash") {
        Seq(r.hash)
      } map { json ⇒
        (checkResponseWrapped compose toResponseWrapped)(json) match {
          case true ⇒
            XGetTransactionByHashRes().withJsonrpc("2.0")
              .withError(emptyError)
          case _ ⇒ JsonFormat.fromJsonString[XGetTransactionByHashRes](json)
        }
      } pipeTo sender

    case r: XGetTransactionReceiptReq ⇒
      sendMessage("eth_getTransactionReceipt") {
        Seq(r.hash)
      } map { json ⇒
        (checkResponseWrapped compose toResponseWrapped)(json) match {
          case true ⇒
            XGetTransactionReceiptRes().withJsonrpc("2.0")
              .withError(emptyError)
          case _ ⇒ JsonFormat.fromJsonString[XGetTransactionReceiptRes](json)
        }
      } pipeTo sender
    case r: XGetBlockWithTxHashByNumberReq ⇒
      sendMessage("eth_getBlockByNumber") {
        Seq(r.blockNumber, false)
      } map { json ⇒
        (checkResponseWrapped compose toResponseWrapped)(json) match {
          case true ⇒
            XGetBlockWithTxHashByNumberRes().withJsonrpc("2.0")
              .withError(emptyError)
          case _ ⇒ JsonFormat.fromJsonString[XGetBlockWithTxHashByNumberRes](json)
        }
      } pipeTo sender
    case r: XGetBlockWithTxObjectByNumberReq ⇒
      sendMessage("eth_getBlockByNumber") {
        Seq(r.blockNumber, true)
      } map { json ⇒
        (checkResponseWrapped compose toResponseWrapped)(json) match {
          case true ⇒
            XGetBlockWithTxObjectByNumberRes().withJsonrpc("2.0")
              .withError(emptyError)
          case _ ⇒ JsonFormat.fromJsonString[XGetBlockWithTxObjectByNumberRes](json)

        }
      } pipeTo sender
    case r: XGetBlockWithTxHashByHashReq ⇒
      sendMessage("eth_getBlockByHash") {
        Seq(r.blockHash, false)
      } map { json ⇒
        (checkResponseWrapped compose toResponseWrapped)(json) match {
          case true ⇒
            XGetBlockWithTxHashByHashRes().withJsonrpc("2.0")
              .withError(emptyError)
          case _ ⇒ JsonFormat.fromJsonString[XGetBlockWithTxHashByHashRes](json)
        }
      } pipeTo sender
    case r: XGetBlockWithTxObjectByHashReq ⇒
      sendMessage("eth_getBlockByHash") {
        Seq(r.blockHash, true)
      } map { json ⇒
        (checkResponseWrapped compose toResponseWrapped)(json) match {
          case true ⇒
            XGetBlockWithTxObjectByHashRes().withJsonrpc("2.0")
              .withError(emptyError)
          case _ ⇒ JsonFormat.fromJsonString[XGetBlockWithTxObjectByHashRes](json)
        }
      } pipeTo sender
    case r: XTraceTransactionReq ⇒
      sendMessage("debug_traceTransaction") {
        val debugParams = DebugParams(DEBUG_TIMEOUT_STR, DEBUG_TRACER)
        Seq(r.txhash, debugParams)
      } map { json ⇒
        (checkResponseWrapped compose toResponseWrapped)(json) match {
          case true ⇒
            XTraceTransactionRes().withJsonrpc("2.0")
              .withError(emptyError)
          case _ ⇒ JsonFormat.fromJsonString[XTraceTransactionRes](json)
        }
      } pipeTo sender
    // 这里因为 SendRawTransactionReq 在 actors 里面定义 暂时注释掉
    //    case r: SendRawTransactionReq ⇒
    //      sendMessage("eth_sendRawTransaction") {
    //        Seq(r.data)
    //      } map { json ⇒
    //        (checkResponseWrapped compose toResponseWrapped)(json) match {
    //          case true ⇒
    //            SendRawTransactionRes().withJsonrpc("2.0")
    //              .withError(emptyError)
    //          case _ ⇒ JsonFormat.fromJsonString[SendRawTransactionRes](json)
    //        }
    //      } pipeTo sender
    case r: XGetEstimatedGasReq ⇒
      sendMessage("eth_estimateGas") {
        val args = XTransactionParam().withTo(r.to).withData(r.data)
        Seq(args)
      } map { json ⇒
        (checkResponseWrapped compose toResponseWrapped)(json) match {
          case true ⇒
            XGetEstimatedGasRes().withJsonrpc("2.0")
              .withError(emptyError)
          case _ ⇒ JsonFormat.fromJsonString[XGetEstimatedGasRes](json)
        }
      } pipeTo sender
    case r: XGetNonceReq ⇒
      sendMessage("eth_getTransactionCount") {
        Seq(r.owner, r.tag)
      } map { json ⇒
        (checkResponseWrapped compose toResponseWrapped)(json) match {
          case true ⇒
            XGetNonceRes().withJsonrpc("2.0")
              .withError(emptyError)
          case _ ⇒ JsonFormat.fromJsonString[XGetNonceRes](json)
        }
      } pipeTo sender
    case r: XGetBlockTransactionCountReq ⇒
      sendMessage("eth_getBlockTransactionCountByHash") {
        Seq(r.blockHash)
      } map { json ⇒
        (checkResponseWrapped compose toResponseWrapped)(json) match {
          case true ⇒
            XGetBlockTransactionCountRes().withJsonrpc("2.0")
              .withError(emptyError)
          case _ ⇒ JsonFormat.fromJsonString[XGetBlockTransactionCountRes](json)
        }
      } pipeTo sender
    case r: XEthCallReq ⇒
      sendMessage("eth_call") {
        Seq(r.param, r.tag)
      } map { json ⇒
        (checkResponseWrapped compose toResponseWrapped)(json) match {
          case true ⇒
            XEthCallRes().withJsonrpc("2.0")
              .withError(emptyError)
          case _ ⇒ JsonFormat.fromJsonString[XEthCallRes](json)
        }
      } pipeTo sender
  }

}

private case class DebugParams(timeout: String, tracer: String)

private class EmptyValueSerializer
  extends CustomSerializer[String](
    _ ⇒
      ({
        case JNull ⇒ ""
      }, {
        case "" ⇒ JNothing
      })
  )

