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

package io.lightcone.relayer.jsonrpc

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import io.lightcone.core.{ErrorCode, ErrorException}
import io.lightcone.relayer.data.{GetOrders, SubmitOrder}
import io.lightcone.relayer.support._

import scala.concurrent.{Await, Future}

class JsonSpec extends CommonSpec with EventExtractorSupport {

  "merge json" must {
    "merge into json correctly" in {

      val order1 = createRawOrder(
        tokenS = LRC_TOKEN.address,
        tokenB = WETH_TOKEN.address
      )
      Await.result(
        singleRequest(SubmitOrder.Req(Some(order1)), "submit_order"),
        timeout.duration
      )

      val reqJson = JsonRpcRequest(
        "2.0",
        "get_orders",
        ps.serialize(GetOrders.Req(owner = accounts.head.getAddress)),
        Some("1")
      )
      for {
        response <- Http().singleRequest(
          HttpRequest(
            method = HttpMethods.POST,
            entity = HttpEntity(
              ContentTypes.`application/json`,
              serialization.write(reqJson)
            ),
            uri = Uri(
              s"http://127.0.0.1:${config.getString("jsonrpc.http.port")}/" +
                s"${config.getString("jsonrpc.endpoint")}/${config.getString("jsonrpc.loopring")}"
            )
          )
        )
        res <- response.status match {
          case StatusCodes.OK =>
            response.entity.toStrict(timeout.duration).map(_.data.utf8String)

          case _ => Future.successful("failed")

        }
      } yield {
        println(res)
      }
    }
  }
}
