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

package org.loopring.lightcone.actors.marketcap

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{ ClientTransport, Http }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Sink, Source }

import scala.concurrent.Future

trait HttpConnector extends Json4sSupport {

  implicit val system: ActorSystem
  implicit val mat: ActorMaterializer

  implicit val ex = system.dispatcher

  val connection: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]]

  val dispatcher = (request: HttpRequest) ⇒
    Source.single(request).via(connection).runWith(Sink.head)

  def https(
    host: String,
    port: Int = 443,
    proxy: Option[Boolean] = None)(
    implicit
    system: ActorSystem): Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] = {
    Http().outgoingConnectionHttps(host, port, settings = getSettings(proxy))
  }

  def http(
    host: String,
    port: Int = 80,
    proxy: Option[Boolean] = None)(
    implicit
    system: ActorSystem): Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] = {
    Http().outgoingConnection(host, port, settings = getSettings(proxy))
  }

  private[this] def getSettings(proxy: Option[Boolean] = None)(implicit system: ActorSystem) = {
    proxy match {
      case Some(true) ⇒
        val httpsProxy = ClientTransport.httpsProxy(
          InetSocketAddress.createUnresolved("127.0.0.1", 1087))
        ClientConnectionSettings(system).withTransport(httpsProxy)
      case _ ⇒ ClientConnectionSettings(system)
    }
  }

  def get[T](uri: String)(implicit fallback: HttpResponse ⇒ Future[T]): Future[T] = {
    val fallbackFuture = (r: Future[HttpResponse]) ⇒ r.flatMap(fallback)
    (dispatcher andThen fallbackFuture)(Get(uri))
  }

  //  def getWithEntity[T](uri: String)(implicit fallback: HttpResponse ⇒ Future[T]): Future[T] = {
  //    // val fallbackFuture = (r: Future[HttpResponse]) ⇒ r.flatMap(fallback)
  //
  //    val f = (r: Future[HttpResponse]) ⇒ {
  //      case HttpResponse(StatusCodes.OK, _, entity) ⇒ Unmarshal(entity).to[T]
  //      case _ ⇒ throw new Exception("")
  //    }
  //
  //    (dispatcher andThen fallbackFuture)(Get(uri))
  //  }

  def get[T](httpRequest: HttpRequest)(implicit fallback: HttpResponse ⇒ Future[T]): Future[T] = {
    val fallbackFuture = (r: Future[HttpResponse]) ⇒ r.flatMap(fallback)
    (dispatcher andThen fallbackFuture)(httpRequest)
  }

  implicit class ResponseTo(response: HttpResponse) extends Unmarshal[HttpResponse](response) {}

}

object Get {
  def apply(uri: String): HttpRequest =
    HttpRequest(method = HttpMethods.GET, uri = Uri(uri))

  def apply(uri: Uri): HttpRequest =
    HttpRequest(method = HttpMethods.GET, uri = uri)
}

object Post {

  def apply(uri: String): HttpRequest =
    HttpRequest(method = HttpMethods.POST, uri = Uri(uri))

  def apply(uri: Uri): HttpRequest =
    HttpRequest(method = HttpMethods.POST, uri = uri)

}

