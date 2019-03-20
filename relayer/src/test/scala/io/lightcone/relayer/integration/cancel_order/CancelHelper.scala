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

package io.lightcone.relayer.integration
import io.lightcone.core.MarketHash
import io.lightcone.ethereum.DefaultEIP712Support
import io.lightcone.lib.NumericConversion
import io.lightcone.relayer.data.CancelOrder
import org.json4s.jackson.JsonMethods.{compact, render}
import org.json4s.native.JsonMethods.parse
import org.json4s.JsonDSL._
import org.web3j.crypto.{Credentials, Sign}
import org.web3j.utils.Numeric

trait CancelHelper {

  implicit val eip712Support = new DefaultEIP712Support()

  val cancelOrderSchema = parse(
    system.settings.config.getString("order_cancel.schema").stripMargin
  )

  def generateCancelOrderSig(
      req: CancelOrder.Req
    )(
      implicit
      credentials: Credentials
    ) = {
    val cancelRequest = Map(
      "id" -> req.id,
      "owner" -> req.owner,
      "market" -> req.marketPair
        .map(
          marketPair =>
            NumericConversion.toHexString(MarketHash(marketPair).bigIntValue)
        )
        .getOrElse(""),
      "time" -> NumericConversion.toHexString(req.getTime)
    )
    val message = Map("message" -> cancelRequest)
    val completedMessage = compact(cancelOrderSchema merge render(message))
    val typedData = eip712Support.jsonToTypedData(completedMessage)
    val hash = eip712Support.getEIP712Message(typedData)
    val sigData = Sign.signPrefixedMessage(
      Numeric.hexStringToByteArray(hash),
      credentials.getEcKeyPair
    )
    val sig = sigData.getR.toSeq ++ sigData.getS.toSeq ++ Seq(sigData.getV)
    Numeric.toHexString(sig.toArray)
  }
}
