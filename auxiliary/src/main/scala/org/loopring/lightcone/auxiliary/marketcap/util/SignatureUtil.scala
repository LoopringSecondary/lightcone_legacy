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

package org.loopring.lightcone.auxiliary.marketcap.util

import java.util.Base64

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

trait SignatureUtil {

  def getHmacSHA256(key: String, content: String): Array[Byte] = {
    val secret = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(secret)
    mac.doFinal(content.getBytes("UTF-8"))
  }

  def encodeToBase64String(bytes: Array[Byte]): String = Base64.getEncoder.encodeToString(bytes)

  def bytesToHex(bytes: Array[Byte]): String = {
    val sb = new StringBuffer();
    for (i ← 0 until bytes.length) {
      val hex = Integer.toHexString(bytes.apply(i) & 0xFF)
      if (hex.length() < 2) {
        sb.append(0)
      }
      sb.append(hex)
    }
    sb.toString
  }

}
