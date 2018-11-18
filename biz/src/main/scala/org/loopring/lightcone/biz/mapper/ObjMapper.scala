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

package org.loopring.lightcone.biz.mapper

// Maps a value from the type A to B
trait ObjMapper[A, B] {
  def mapValue(obj: A): B
}

object ObjMapper {

  @inline
  def mapValue[A, B](obj: A)(implicit mapper: ObjMapper[A, B]): B = {
    mapper.mapValue(obj)
  }
}
