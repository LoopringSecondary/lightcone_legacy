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

trait Constants {

  private val rand = new scala.util.Random(31)

  val LRC = "0x0000012341111111111111"
  val GTO = "0x000002222442222222222"
  val DAI = "0x0000033333242333333333"
  val WETH = "0x003424444444444444444"

  val TOKENS = Seq(LRC, GTO, DAI, WETH)

  def randomToken() = {
    TOKENS(rand.nextInt(TOKENS.size))
  }

  object Addr {
    def apply() = rand.alphanumeric.take(22).mkString("")
    def apply(idx: Int) = addresses(idx)
    val addresses = Seq("addr0", "addr1", "addr2")
  }
}
