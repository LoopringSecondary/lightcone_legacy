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

// TODO(hongyu): implement the SplitMerger for GetAccounts
class DefaultSplitMergerProvider extends RegisterableSplitMergerProvider {

  // The following are demo code
  register(new SplitMerger[String, String, String, String] {
    def split(req: String) = Seq(req)
    def merge(resps: Seq[String]) = resps.reduce(_ + _)
  })

}
