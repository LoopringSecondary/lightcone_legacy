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
import akka.cluster.singleton._
import akka.http.scaladsl._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.stream._
import akka.stream.scaladsl._
import akka.util.Timeout
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.native.JsonMethods.parse
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.lib.TimeProvider
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto._
import scalapb.json4s.JsonFormat

import scala.collection.JavaConverters._
import scala.concurrent._
import scala.util._

object HttpConnector {
  val name = "http_connector"

  def connectorNames(
    config: Config): Map[String, EthereumProxySettings.Node] = {
    config
      .getConfigList("ethereum_client_monitor.nodes")
      .asScala
      .zipWithIndex
      .map {
        case (c, index) =>
          val node = EthereumProxySettings
            .Node(host = c.getString("host"), port = c.getInt("port"))
          s"${name}_$index" -> node
      }
      .toMap
  }

  def start(
    implicit system: ActorSystem,
    config: Config,
    ec: ExecutionContext,
    timeProvider: TimeProvider,
    timeout: Timeout,
    actors: Lookup[ActorRef],
    dbModule: DatabaseModule,
    deployActorsIgnoringRoles: Boolean): Map[String, ActorRef] = {

    val materializer = ActorMaterializer()(system)
    connectorNames(config).map {
      case (nodeName, node) =>
        val roleOpt = if (deployActorsIgnoringRoles) None else Some(nodeName)
        system.actorOf(
          ClusterSingletonManager.props(
            singletonProps = Props(new HttpConnector(node)(materializer)),
            terminationMessage = PoisonPill,
            settings = ClusterSingletonManagerSettings(system).withRole(roleOpt)),
          nodeName)
        nodeName -> system.actorOf(
          ClusterSingletonProxy.props(
            singletonManagerPath = s"/user/${nodeName}",
            settings = ClusterSingletonProxySettings(system)),
          name = s"${nodeName}_proxy")
    }
  }
}

// Owner: Yadong
class HttpConnector(
  node: EthereumProxySettings.Node)(
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
  val LATEST = "latest"
  val JSONRPC_V = "2.0"

  val emptyError = EthRpcError(code = 500)

  private val poolClientFlow: Flow[ //
  (HttpRequest, Promise[HttpResponse]), //
  (Try[HttpResponse], Promise[HttpResponse]), //
  Http.HostConnectionPool] = {
    Http().cachedHostConnectionPool[Promise[HttpResponse]](
      host = node.host,
      port = node.port)
  }

  log.debug(s"connecting Ethereum at ${node.host}:${node.port}")

  private val queue: SourceQueueWithComplete[(HttpRequest, Promise[HttpResponse])] =
    Source
      .queue[(HttpRequest, Promise[HttpResponse])](
        500,
        OverflowStrategy.backpressure)
      .via(poolClientFlow)
      .toMat(Sink.foreach({
        case (Success(resp), p) => p.success(resp)
        case (Failure(e), p) => p.failure(e)
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
        HttpRequest(method = HttpMethods.POST, entity = entity))
      jsonStr <- httpResp.entity.dataBytes.map(_.utf8String).runReduce(_ + _)
    } yield jsonStr
  }

  private def sendMessage(method: String)(params: Seq[Any]): Future[String] = {
    val jsonRpc = JsonRpcReqWrapped(
      id = Random.nextInt(100),
      jsonrpc = JSONRPC_V,
      method = method,
      params = params)
    log.debug(s"reqeust: ${org.json4s.native.Serialization.write(jsonRpc)}")

    for {
      entity <- Marshal(jsonRpc).to[RequestEntity]
      jsonStr <- post(entity)
      _ = log.debug(s"response: $jsonStr")
    } yield jsonStr

  }
  private def batchSendMessages(
    methodList: Seq[BatchMethod]): Future[String] = {
    val jsonRpcList = methodList.map { x =>
      JsonRpcReqWrapped(
        id = if (x.id >= 0) x.id else randInt(),
        jsonrpc = JSONRPC_V,
        method = x.method,
        params = x.params)
    }

    for {
      entity <- Marshal(jsonRpcList).to[RequestEntity]
      jsonStr <- post(entity)
    } yield jsonStr
  }

  def receive: Receive = {
    case req: JsonRpc.Request =>
      post(req.json).map(JsonRpc.Response(_)) sendTo sender

    case r: SendRawTransaction.Req =>
      sendMessage("eth_sendRawTransaction") {
        Seq(r.data)
      } map JsonFormat.fromJsonString[SendRawTransaction.Res] sendTo sender

    case _: GetBlockNumber.Req =>
      sendMessage("eth_blockNumber") {
        Seq.empty
      } map JsonFormat.fromJsonString[GetBlockNumber.Res] sendTo sender

    case r: EthGetBalance.Req =>
      sendMessage("eth_getBalance") {
        Seq(r.address, r.tag)
      } map JsonFormat.fromJsonString[EthGetBalance.Res] sendTo sender

    case r: GetTransactionByHash.Req =>
      sendMessage("eth_getTransactionByHash") {
        Seq(r.hash)
      } map JsonFormat.fromJsonString[GetTransactionByHash.Res] sendTo sender

    case r: GetTransactionReceipt.Req =>
      sendMessage("eth_getTransactionReceipt") {
        Seq(r.hash)
      } map JsonFormat.fromJsonString[GetTransactionReceipt.Res] sendTo sender

    case r: GetBlockWithTxHashByNumber.Req =>
      sendMessage("eth_getBlockByNumber") {
        Seq(r.blockNumber, false)
      } map JsonFormat
        .fromJsonString[GetBlockWithTxHashByNumber.Res] sendTo sender

    case r: GetBlockWithTxObjectByNumber.Req =>
      sendMessage("eth_getBlockByNumber") {
        Seq(r.blockNumber, true)
      } map JsonFormat
        .fromJsonString[GetBlockWithTxObjectByNumber.Res] sendTo sender

    case r: GetBlockWithTxHashByHash.Req =>
      sendMessage("eth_getBlockByHash") {
        Seq(r.blockHash, false)
      } map JsonFormat
        .fromJsonString[GetBlockWithTxHashByHash.Res] sendTo sender

    case r: GetBlockWithTxObjectByHash.Req =>
      sendMessage("eth_getBlockByHash") {
        Seq(r.blockHash, true)
      } map JsonFormat
        .fromJsonString[GetBlockWithTxObjectByHash.Res] sendTo sender

    case r: TraceTransaction.Req =>
      sendMessage("debug_traceTransaction") {
        val debugParams = DebugParams(DEBUG_TIMEOUT_STR, DEBUG_TRACER)
        Seq(r.txhash, debugParams)
      } map JsonFormat.fromJsonString[TraceTransaction.Res] sendTo sender

    case r: GetEstimatedGas.Req =>
      sendMessage("eth_estimateGas") {
        val args = TransactionParams().withTo(r.to).withData(r.data)
        Seq(args)
      } map JsonFormat.fromJsonString[GetEstimatedGas.Res] sendTo sender

    case r: GetNonce.Req =>
      sendMessage("eth_getTransactionCount") {
        Seq(r.owner, r.tag)
      } map JsonFormat.fromJsonString[GetNonce.Res] sendTo sender

    case r: GetBlockTransactionCount.Req =>
      sendMessage("eth_getBlockTransactionCountByHash") {
        Seq(r.blockHash)
      } map JsonFormat
        .fromJsonString[GetBlockTransactionCount.Res] sendTo sender

    case r @ EthCall.Req(_, param, _) =>
      sendMessage("eth_call") {
        Seq(param, normalizeTag(r.tag))
      } map JsonFormat.fromJsonString[EthCall.Res] sendTo sender

    case r: GetUncle.Req =>
      sendMessage(method = "eth_getUncleByBlockNumberAndIndex") {
        Seq(r.blockNum, r.index)
      } map JsonFormat
        .fromJsonString[GetBlockWithTxHashByHash.Res] sendTo sender

    case batchR: BatchCallContracts.Req =>
      val batchReqs = batchR.reqs.map { singleReq =>
        {
          BatchMethod(
            id = singleReq.id,
            method = "eth_call",
            params = Seq(singleReq.param, normalizeTag(singleReq.tag)))
        }

      }

      //这里无法直接解析成BatchCallContracts.Res
      batchSendMessages(batchReqs) map { json =>
        val resps = parse(json).values.asInstanceOf[List[Map[String, Any]]]
        val callResps = resps.map(resp => {
          val respJson = Serialization.write(resp)
          JsonFormat.fromJsonString[EthCall.Res](respJson)
        })
        BatchCallContracts.Res(resps = callResps)
      } sendTo sender

    case batchR: BatchGetTransactionReceipts.Req =>
      val batchReqs = batchR.reqs.map { singleReq =>
        BatchMethod(
          id = randInt(),
          method = "eth_getTransactionReceipt",
          params = Seq(singleReq.hash))
      }
      //这里无法直接解析成BatchGetTransactionReceipts.Res
      batchSendMessages(batchReqs) map { json =>
        val resps = parse(json).values.asInstanceOf[List[Map[String, Any]]]
        val receiptResps = resps.map(resp => {
          val respJson = Serialization.write(resp)
          JsonFormat.fromJsonString[GetTransactionReceipt.Res](respJson)
        })
        BatchGetTransactionReceipts.Res(resps = receiptResps)
      } sendTo sender

    case batchR: BatchGetTransactions.Req =>
      val batchReqs = batchR.reqs.map { singleReq =>
        BatchMethod(
          id = randInt(),
          method = "eth_getTransactionByHash",
          params = Seq(singleReq.hash))
      }
      //这里无法直接解析成BatchGetTransactions.Res
      batchSendMessages(batchReqs) map { json =>
        val resps = parse(json).values.asInstanceOf[List[Map[String, Any]]]
        val txResps = resps.map(resp => {
          val respJson = Serialization.write(resp)
          JsonFormat.fromJsonString[GetTransactionByHash.Res](respJson)
        })
        BatchGetTransactions.Res(resps = txResps)
      } sendTo sender

    case batchR: BatchGetUncle.Req => {
      val batchReqs = batchR.reqs.map { singleReq =>
        BatchMethod(
          id = randInt(),
          method = "eth_getUncleByBlockNumberAndIndex",
          params = Seq(singleReq.blockNum, singleReq.index))
      }
      batchSendMessages(batchReqs) map { json =>
        val resps = parse(json).values.asInstanceOf[List[Map[String, Any]]]
        val txResps = resps.map(resp => {
          val respJson = Serialization.write(resp)
          JsonFormat.fromJsonString[GetBlockWithTxHashByHash.Res](respJson)
        })
        BatchGetUncle.Res(txResps)
      } sendTo sender
    }

    case batchR: BatchGetEthBalance.Req => {
      val batchReqs = batchR.reqs.map { singleReq =>
        {
          BatchMethod(
            id = randInt(),
            method = "eth_getBalance",
            params = Seq(singleReq.address, normalizeTag(singleReq.tag)))
        }
      }
      batchSendMessages(batchReqs) map { json =>
        val resps = parse(json).values.asInstanceOf[List[Map[String, Any]]]
        val txResps = resps.map(resp => {
          val respJson = Serialization.write(resp)
          JsonFormat.fromJsonString[EthGetBalance.Res](respJson)
        })
        BatchGetEthBalance.Res(txResps)
      } sendTo sender
    }
  }

  private def normalizeTag(tag: String) =
    if (tag == null || tag.isEmpty) LATEST else tag

  private def randInt() = Random.nextInt(100000)

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
      }))
