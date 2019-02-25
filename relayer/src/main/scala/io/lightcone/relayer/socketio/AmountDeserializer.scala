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

package io.lightcone.relayer.socketio

import java.io.IOException

import com.corundumstudio.socketio.protocol.Event
import com.fasterxml.jackson.core.{JsonParser, JsonToken}
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import io.lightcone.core.Amount
import io.lightcone.lib.NumericConversion
import io.lightcone.core._

class AmountDeserializer(_valueClass: Class[Amount] = classOf[Amount])
    extends StdDeserializer[Amount](_valueClass) {

  override def deserialize(
      p: JsonParser,
      ctxt: DeserializationContext
    ): Amount = {
    var hasNext = true
    var amount: Amount = null
    while (hasNext) {
      val token = p.nextToken()
      if (token == JsonToken.FIELD_NAME) {
        if (p.getText().equalsIgnoreCase("value")) {
          if (amount == null) {
            amount = NumericConversion.toAmount(p.nextTextValue())
          } else {
            amount =
              amount.withValue(NumericConversion.toBigInt(p.nextTextValue()))
          }
        }

        if (p.getText().equalsIgnoreCase("block")) {
          if (amount == null) {
            amount = Amount(blockNum = p.nextLongValue(-1L))
          } else {
            amount = amount.withBlockNum(p.nextLongValue(-1L))
          }
        }
      }
      if (token == JsonToken.END_OBJECT) {
        hasNext = false
      }
    }
    if (amount != null) {
      amount
    } else throw new IOException("expect hex string for value of amount")

  }
}
