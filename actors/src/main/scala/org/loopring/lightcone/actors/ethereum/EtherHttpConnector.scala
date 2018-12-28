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

package org.loopring.lightcone.actors.ethereum

import akka.actor._
import akka.http.scaladsl._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.stream._
import akka.stream.scaladsl._
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s._
import org.json4s.native.JsonMethods.parse
import org.json4s.jackson.Serialization
import org.loopring.lightcone.proto._
import scalapb.json4s.JsonFormat
import org.loopring.lightcone.actors.base.safefuture._

import scala.concurrent._
import scala.util._

private[ethereum] class HttpConnector(
    node: XEthereumProxySettings.XNode
  )(
    implicit val mat: ActorMaterializer)
    extends Actor
    with ActorLogging
    with Json4sSupport {

  import context.dispatcher

  implicit val serialization = jackson.Serialization //.formats(NoTypeHints)
  implicit val system: ActorSystem = context.system
  implicit val formats = org.json4s.native.Serialization
    .formats(NoTypeHints) + new EmptyValueSerializer

  val DEBUG_TIMEOUT_STR = "5s"
  val DEBUG_TRACER = "callTracer"
  val ETH_CALL = "eth_call"

  val emptyError = XEthResError(code = 500, error = "result is empty")

  private val poolClientFlow: Flow[
    (HttpRequest, Promise[HttpResponse]),
    (Try[HttpResponse], Promise[HttpResponse]),
    Http.HostConnectionPool
  ] = {
    Http().cachedHostConnectionPool[Promise[HttpResponse]](
      host = node.host,
      port = node.port
    )
  }

  log.debug(s"connecting Ethereum at ${node.host}:${node.port}")

  private val queue
    : SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] =
    Source
      .queue[(HttpRequest, Promise[HttpResponse])](
        500,
        OverflowStrategy.backpressure
      )
      .via(poolClientFlow)
      .toMat(Sink.foreach({
        case (Success(resp), p) => p.success(resp)
        case (Failure(e), p)    => p.failure(e)
      }))(Keep.left)
      .run()(mat)

  private def request(request: HttpRequest): Future[HttpResponse] = {
    val responsePromise = Promise[HttpResponse]()
    queue.offer(request -> responsePromise).flatMap {
      case QueueOfferResult.Enqueued =>
        responsePromise.future
      case QueueOfferResult.Dropped =>
        Future.failed(new RuntimeException("Queue overflowed."))
      case QueueOfferResult.Failure(ex) =>
        Future.failed(ex)
      case QueueOfferResult.QueueClosed =>
        Future.failed(new RuntimeException("Queue closed."))
    }
  }

  private def post(json: String): Future[String] = {
    post(HttpEntity(ContentTypes.`application/json`, json))
  }

  private def post(entity: RequestEntity): Future[String] = {
    for {
      httpResp <- request(
        HttpRequest(method = HttpMethods.POST, entity = entity)
      )
      jsonStr <- httpResp.entity.dataBytes.map(_.utf8String).runReduce(_ + _)
    } yield jsonStr
  }

  private def sendMessage(method: String)(params: Seq[Any]): Future[String] = {
    val jsonRpc = JsonRpcReqWrapped(
      id = Random.nextInt(100),
      jsonrpc = "2.0",
      method = method,
      params = params
    )
    log.debug(s"reqeust: ${org.json4s.native.Serialization.write(jsonRpc)}")

    for {
      entity <- Marshal(jsonRpc).to[RequestEntity]
      jsonStr <- post(entity)
      _ = log.debug(s"response: $jsonStr")
    } yield jsonStr

  }
  private def batchSendMessages(
      methodList: Seq[BatchMethod]
    ): Future[String] = {
    val jsonRpcList = methodList.map { x =>
      JsonRpcReqWrapped(
        id = if (x.id >= 0) x.id else Random.nextInt(10000),
        jsonrpc = "2.0",
        method = x.method,
        params = x.params
      )
    }

    for {
      entity <- Marshal(jsonRpcList).to[RequestEntity]
      jsonStr <- post(entity)
    } yield jsonStr
  }

  private def toResponseWrapped: PartialFunction[String, JsonRpcResWrapped] = {
    case json: String => parse(json).extract[JsonRpcResWrapped]
  }

  private def toResponseListWrapped
    : PartialFunction[String, Seq[JsonRpcResWrapped]] = {
    case json: String => parse(json).extract[Seq[JsonRpcResWrapped]]
  }

  private def checkResponseWrapped
    : PartialFunction[JsonRpcResWrapped, Boolean] = {
    case res: JsonRpcResWrapped => res.result.toString.isEmpty
  }

  private def checkResponseListWrapped
    : PartialFunction[Seq[JsonRpcResWrapped], Boolean] = {
    case res: Seq[JsonRpcResWrapped] => res.isEmpty
  }

  private def hex2BigInt(s: String) = BigInt(s.replace("0x", ""), 16)

  def receive: Receive = {
    case req: XJsonRpcReq =>
      post(req.json).map(XJsonRpcRes(_)) sendTo sender

    case _: XEthBlockNumberReq =>
      sendMessage("eth_blockNumber") {
        Seq.empty
      } map JsonFormat.fromJsonString[XEthBlockNumberRes] sendTo sender

    case r: XEthGetBalanceReq =>
      sendMessage("eth_getBalance") {
        Seq(r.address, r.tag)
      } map JsonFormat.fromJsonString[XEthGetBalanceRes] sendTo sender

    case r: GetTransactionByHashReq =>
      sendMessage("eth_getTransactionByHash") {
        Seq(r.hash)
      } map JsonFormat.fromJsonString[GetTransactionByHashRes] sendTo sender

    case r: GetTransactionReceiptReq =>
      sendMessage("eth_getTransactionReceipt") {
        Seq(r.hash)
      } map JsonFormat.fromJsonString[GetTransactionReceiptRes] sendTo sender

    case r: GetBlockWithTxHashByNumberReq =>
      sendMessage("eth_getBlockByNumber") {
        Seq(r.blockNumber, false)
      } map JsonFormat
        .fromJsonString[GetBlockWithTxHashByNumberRes] sendTo sender

    case r: GetBlockWithTxObjectByNumberReq =>
      sendMessage("eth_getBlockByNumber") {
        Seq(r.blockNumber, true)
      } map JsonFormat
        .fromJsonString[GetBlockWithTxObjectByNumberRes] sendTo sender

    case r: GetBlockWithTxHashByHashReq =>
      sendMessage("eth_getBlockByHash") {
        Seq(r.blockHash, false)
      } map JsonFormat
        .fromJsonString[GetBlockWithTxHashByHashRes] sendTo sender

    case r: GetBlockWithTxObjectByHashReq =>
      sendMessage("eth_getBlockByHash") {
        Seq(r.blockHash, true)
      } map JsonFormat
        .fromJsonString[GetBlockWithTxObjectByHashRes] sendTo sender

    case r: XTraceTransactionReq =>
      sendMessage("debug_traceTransaction") {
        val debugParams = DebugParams(DEBUG_TIMEOUT_STR, DEBUG_TRACER)
        Seq(r.txhash, debugParams)
      } map JsonFormat.fromJsonString[XTraceTransactionRes] sendTo sender

    case r: GetEstimatedGasReq =>
      sendMessage("eth_estimateGas") {
        val args = XTransactionParam().withTo(r.to).withData(r.data)
        Seq(args)
      } map JsonFormat.fromJsonString[GetEstimatedGasRes] sendTo sender

    case r: GetNonceReq =>
      sendMessage("eth_getTransactionCount") {
        Seq(r.owner, r.tag)
      } map JsonFormat.fromJsonString[GetNonceRes] sendTo sender

    case r: GetBlockTransactionCountReq =>
      sendMessage("eth_getBlockTransactionCountByHash") {
        Seq(r.blockHash)
      } map JsonFormat
        .fromJsonString[GetBlockTransactionCountRes] sendTo sender

    case r: XEthCallReq =>
      sendMessage("eth_call") {
        Seq(r.param, r.tag)
      } map JsonFormat.fromJsonString[XEthCallRes] sendTo sender

    case r: GetUncleByBlockNumAndIndexReq ⇒
      sendMessage(method = "eth_getUncleByBlockNumberAndIndex") {
        Seq(r.blockNum, r.index)
      } map JsonFormat
        .fromJsonString[GetBlockWithTxHashByHashRes] sendTo sender

    case batchR: XBatchContractCallReq =>
      val batchReqs = batchR.reqs.map { singleReq =>
        BatchMethod(
          id = singleReq.id,
          method = "eth_call",
          params = Seq(singleReq.param, singleReq.tag)
        )
      }
      //这里无法直接解析成XBatchContractCallRes
      batchSendMessages(batchReqs) map { json =>
        val resps = parse(json).values.asInstanceOf[List[Map[String, Any]]]
        val callResps = resps.map(resp => {
          val respJson = Serialization.write(resp)
          JsonFormat.fromJsonString[XEthCallRes](respJson)
        })
        XBatchContractCallRes(resps = callResps)
      } sendTo sender

    case batchR: XBatchGetTransactionReceiptsReq =>
      val batchReqs = batchR.reqs.map { singleReq =>
        BatchMethod(
          id = 0,
          method = "eth_getTransactionReceipt",
          params = Seq(singleReq.hash)
        )
      }
      //这里无法直接解析成XBatchGetTransactionReceiptsRes
      batchSendMessages(batchReqs) map { json =>
        val resps = parse(json).values.asInstanceOf[List[Map[String, Any]]]
        val receiptResps = resps.map(resp => {
          val respJson = Serialization.write(resp)
          JsonFormat.fromJsonString[GetTransactionReceiptRes](respJson)
        })
        XBatchGetTransactionReceiptsRes(resps = receiptResps)
      } sendTo sender

    case batchR: XBatchGetTransactionsReq =>
      val batchReqs = batchR.reqs.map { singleReq =>
        BatchMethod(
          id = 0,
          method = "eth_getTransactionByHash",
          params = Seq(singleReq.hash)
        )
      }
      //这里无法直接解析成XBatchGetTransactionsRes
      batchSendMessages(batchReqs) map { json =>
        val resps = parse(json).values.asInstanceOf[List[Map[String, Any]]]
        val txResps = resps.map(resp => {
          val respJson = Serialization.write(resp)
          JsonFormat.fromJsonString[GetTransactionByHashRes](respJson)
        })
        XBatchGetTransactionsRes(resps = txResps)
      } sendTo sender

    case batchR: XBatchGetUncleByBlockNumAndIndexReq ⇒ {
      val batchReqs = batchR.reqs.map { singleReq ⇒
        BatchMethod(
          id = 0,
          method = "eth_getUncleByBlockNumberAndIndex",
          params = Seq(singleReq.blockNum, singleReq.index)
        )
      }
      batchSendMessages(batchReqs) map { json ⇒
        val resps = parse(json).values.asInstanceOf[List[Map[String, Any]]]
        val txResps = resps.map(resp ⇒ {
          val respJson = Serialization.write(resp)
          JsonFormat.fromJsonString[GetBlockWithTxHashByHashRes](respJson)
        })
        XBatchGetUncleByBlockNumAndIndexRes(txResps)
      } sendTo sender
    }
  }

}

private case class DebugParams(
    timeout: String,
    tracer: String)

private class EmptyValueSerializer
    extends CustomSerializer[String](
      _ =>
        ({
          case JNull => ""
        }, {
          case "" => JNothing
        })
    )
