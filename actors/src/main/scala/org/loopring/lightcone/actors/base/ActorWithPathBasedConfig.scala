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

package org.loopring.lightcone.actors.base

import akka.actor._
import com.typesafe.config.Config
import scala.concurrent._

trait NamedBasedConfig {
  val name: String
  val config: Config // the global config
  protected val selfConfig_ = config.getConfig(name)
  val selfConfig = selfConfig_
}

abstract class ActorWithPathBasedConfig(
    val name: String,
    val extractEntityName: String ⇒ String = Predef.identity
)
  extends Actor
  with ActorLogging
  with NamedBasedConfig {
  protected val entityName = extractEntityName(self.path.name).replace("$", "")

  override val selfConfig = try {
    selfConfig_.getConfig(entityName).withFallback(selfConfig_)
  } catch {
    case e: Throwable ⇒
      log.warning(s"NO CONFIG FOUND for actor with path: ${self.path.name}")
      selfConfig_
  }

  log.info(s"""
    >>> ----------------
    >>> actor created: ${self.path.name}
    >>> entity name: ${entityName}
    >>> config: ${selfConfig}
    >>> ----------------
  """)
}
