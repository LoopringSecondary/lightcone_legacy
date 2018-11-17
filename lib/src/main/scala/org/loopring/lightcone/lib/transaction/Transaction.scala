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

package org.loopring.lightcone.lib

case class Transaction(
    hash: String = "0x",
    blockHash: String = "0x",
    blockNumber: BigInt = 0,
    transactionIndex: Int = 0,
    from: String = "0x",
    to: String = "0x",
    value: BigInt = 0,
    gasPrice: BigInt = 0,
    gas: BigInt = 0,
    input: String = "0x",
    r: String = "0x",
    s: String = "0x",
    v: String = "0x"
)
