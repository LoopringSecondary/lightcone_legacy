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

package org.loopring.lightcone.auxiliary.database.entity

import org.loopring.lightcone.auxiliary.database.base.BaseEntity

case class OrderChangeLogEntity(
    id: Long = 0,
    updatedAt: Long = 0,
    createdAt: Long = 0,
    preChangeId: Long = 0,
    orderHash: String = "",
    dealtAmountS: String = "",
    dealtAmountB: String = "",
    cancelledAmountS: String = "",
    cancelledAmountB: String = "",
    status: String = "",
    updatedBlock: Long = 0
) extends BaseEntity

