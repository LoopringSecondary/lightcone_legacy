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

package io.lightcone.relayer

import io.lightcone.relayer.support._
import io.lightcone.relayer.data._
import io.lightcone.relayer.base._
import io.lightcone.relayer.actors._
import io.lightcone.core._
import scala.concurrent.Await
import scala.concurrent.duration._

import java.io.File

import akka.actor._
import com.google.inject.Guice
import com.google.inject.Injector
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import net.codingwell.scalaguice.InjectorExtensions._
import org.slf4s.Logging
import scala.io.StdIn
import scala.util.Try

import org.scalatest._
import org.slf4s.Logging

// Please make sure in `mysql.conf` all database dals use the same database configuration.
class ExampleIntegrationSpec extends IntegrationTesting {

  "foo" must "bar" in {
    val order: RawOrder = Addr(1) >> 12.1.lrc -> 23.0.weth -- 10.0.lrc
    val req = entrypoint ?? [SubmitOrder.Res] SubmitOrder.Req(Some(order))
  }
}
