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

package org.loopring.lightcone.gateway_bak.inject

import com.google.inject.assistedinject.FactoryModuleBuilder
import com.google.inject.{Binder, Module}
import net.codingwell.scalaguice.InternalModule
import scala.reflect.{ClassTag, _}

trait AssistedInjectFactoryModule[B <: Binder] extends Module {
  self: InternalModule[B] =>

  protected[this] def bindFactory[C <: Any: ClassTag, F: ClassTag](): Unit =
    bindFactory[C, C, F]()

  protected[this] def bindFactory[I: ClassTag, C <: I: ClassTag, F: ClassTag](
    ): Unit =
    binderAccess
      .install(
        new FactoryModuleBuilder()
          .implement(
            classTag[I].runtimeClass.asInstanceOf[Class[I]],
            classTag[C].runtimeClass.asInstanceOf[Class[C]]
          )
          .build(classTag[F].runtimeClass.asInstanceOf[Class[F]])
      )
}
