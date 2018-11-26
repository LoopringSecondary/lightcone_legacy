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

package org.loopring.lightcone.lib.abi

import org.loopring.lightcone.lib.data._

class BrokerRegisterABI(jsonStr: String) extends AbiWrap(jsonStr) {

  val FN_REGISTER_BROKER = "registerBroker"
  val FN_UNREGISTER_BROKER = "unregisterBroker"
  val FN_UNREGISTER_ALL_BROKER = "unregisterAllBrokers"

  val EN_BROKER_REGISTERED = "BrokerRegistered"
  val EN_BROKER_UNREGISTERED = "BrokerUnregistered"
  val EN_ALL_BROKERS_UNREGISTERED = "AllBrokersUnregistered"

  val registerBroker = findFunctionByName(FN_REGISTER_BROKER)
  val unregisterBroker = findFunctionByName(FN_UNREGISTER_BROKER)
  val unregisterAllBrokers = findFunctionByName(FN_UNREGISTER_ALL_BROKER)

  def decodeAndAssemble(tx: Transaction): Option[Any] = {
    val result = decode(tx.input)
    val data = result.name match {
      case FN_REGISTER_BROKER ⇒ assembleRegisterBrokerFunction(result.list, tx.from)
      case FN_UNREGISTER_BROKER ⇒ assembleUnRegisterBrokerFunction(result.list, tx.from)
      case FN_UNREGISTER_ALL_BROKER ⇒ assembleUnRegisterAllBrokerFunction(result.list, tx.from)
      case _ ⇒ None
    }
    Some(data)
  }

  def decodeAndAssemble(tx: Transaction, log: TransactionLog): Option[Any] = {
    val result = decode(log)
    val data = result.name match {
      case EN_BROKER_REGISTERED ⇒ assembleRegisterBrokerEvent(result.list)
      case EN_BROKER_UNREGISTERED ⇒ assembleUnRegisterBrokerEvent(result.list)
      case EN_ALL_BROKERS_UNREGISTERED ⇒ assembleUnRegisterAllBrokerEvent(result.list)
      case _ ⇒ None
    }
    Some(data)
  }

  private[lib] def assembleRegisterBrokerFunction(list: Seq[Any], from: String) = {
    assert(list.length == 2, "length of register broker function invalid")

    BrokerRegistered(
      broker = scalaAny2Hex(list(0)),
      interceptor = scalaAny2Hex(list(1)),
      owner = from
    )
  }

  private[lib] def assembleRegisterBrokerEvent(list: Seq[Any]) = {
    assert(list.length == 3, "length of register broker event invalid")

    BrokerRegistered(
      owner = scalaAny2Hex(list(0)),
      broker = scalaAny2Hex(list(1)),
      interceptor = scalaAny2Hex(list(2))
    )
  }

  private[lib] def assembleUnRegisterBrokerFunction(list: Seq[Any], from: String) = {
    assert(list.length == 1, "length of unregister broker function invalid")

    BrokerUnregistered(
      broker = scalaAny2Hex(list(0)),
      owner = from,
      interceptor = "0x0"
    )
  }

  private[lib] def assembleUnRegisterBrokerEvent(list: Seq[Any]) = {
    assert(list.length == 3, "length of unregister broker event invalid")

    BrokerUnregistered(
      owner = scalaAny2Hex(list(0)),
      broker = scalaAny2Hex(list(1)),
      interceptor = scalaAny2Hex(list(2))
    )
  }

  private[lib] def assembleUnRegisterAllBrokerFunction(list: Seq[Any], from: String) = {
    assert(list.length == 1, "length of unregister broker function invalid")

    AllBrokersUnregistered(
      owner = from
    )
  }

  private[lib] def assembleUnRegisterAllBrokerEvent(list: Seq[Any]) = {
    assert(list.length == 1, "length of unregister broker event invalid")

    AllBrokersUnregistered(
      owner = scalaAny2Hex(list(0))
    )
  }
}
