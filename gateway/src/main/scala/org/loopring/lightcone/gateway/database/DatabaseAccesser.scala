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

package org.loopring.lightcone.gateway.database

import akka.stream.ActorMaterializer
import akka.stream.alpakka.slick.scaladsl.{ Slick, SlickSession }
import akka.stream.scaladsl.{ Sink, Source }
import slick.jdbc.{ GetResult, SQLActionBuilder }

import scala.concurrent.Future

abstract class DatabaseAccesser(
  implicit
  mat: ActorMaterializer,
  session: SlickSession) {

  type ResultRow = slick.jdbc.PositionedResult
  type ResultInt = slick.dbio.DBIO[Int]

  def saveOrUpdate[T](params: T*)(implicit fallback: T ⇒ ResultInt): Future[Int] = {
    Source[T](params.to[collection.immutable.Seq]).via(Slick.flow(fallback)) runReduce (_ + _)
  }

  implicit class SQL(sql: SQLActionBuilder) {

    def list[T](implicit fallback: ResultRow ⇒ T): Future[Seq[T]] = {
      implicit val result: GetResult[T] = GetResult(fallback)
      Slick.source(sql.as[T]).runWith(Sink.seq)
    }

    def head[T](implicit fallback: ResultRow ⇒ T): Future[T] = {
      implicit val result: GetResult[T] = GetResult(fallback)
      Slick.source(sql.as[T]).runWith(Sink.head)
    }

  }

}
