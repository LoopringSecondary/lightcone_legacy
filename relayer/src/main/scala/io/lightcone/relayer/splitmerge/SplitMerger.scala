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

trait SplitMerger[REQ, SUB_REQ, SUB_RES, RES] {

  final def split(req: Any) =
    splitRequest(req.asInstanceOf[REQ])

  final def merge(resps: Seq[Any]) =
    mergeResponses(resps.asInstanceOf[Seq[SUB_RES]])

  def splitRequest(req: REQ): Seq[SUB_REQ]

  def mergeResponses(resps: Seq[SUB_RES]): RES
}
