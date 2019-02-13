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

package io.lightcone.core.testing

import org.scalatest._
import org.scalamock.scalatest._
import org.slf4s.Logging
import scala.concurrent._
import scala.concurrent.duration._

trait CommonSpec
    extends FlatSpec
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with Matchers
    with MockFactory
    with Constants
    with OrderHelper
    with Logging {

  override def beforeAll(): Unit = {
    println(
      s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`"
    )
  }

  implicit class RichFuture[T](f: => Future[T]) {
    def await(): T = Await.result(f, Duration.Inf)
  }
}
