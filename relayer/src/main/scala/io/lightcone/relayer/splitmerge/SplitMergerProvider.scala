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

package io.lightcone.relayer.splitmerge

import scala.reflect.ClassTag

trait SplitMergerProvider {
  def get(msg: Any): Option[SplitMerger[_, _, _, _]]
}

abstract class RegisterableSplitMergerProvider extends SplitMergerProvider {

  private var registry = Map.empty[Class[_], SplitMerger[_, _, _, _]]

  protected def register[T: ClassTag](splitMerger: SplitMerger[T, _, _, _]) = {
    val klass = implicitly[ClassTag[T]].runtimeClass
    assert(!registry.contains(klass))
    registry += klass -> splitMerger
  }

  def get(msg: Any) = registry.get(msg.getClass)
}
