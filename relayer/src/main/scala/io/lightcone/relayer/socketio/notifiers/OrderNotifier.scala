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

// /*
//  * Copyright 2018 Loopring Foundation
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *     http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */

// package io.lightcone.relayer.socketio.notifiers

// import com.corundumstudio.socketio.SocketIOClient
// import com.google.inject.Inject
// import io.lightcone.core._
// import io.lightcone.lib.Address
// import io.lightcone.relayer.data.SocketIOSubscription
// import io.lightcone.relayer.socketio._

// class OrderNotifier @Inject()
//     extends SocketIONotifier[SocketIOSubscription.ParamsForOrders] {

//   val name = "orders"

//   def isSubscriptionValid(subscription: SocketIOSubscription): Boolean =
//     subscription.paramsForOrders.isDefined &&
//       subscription.getParamsForOrders.addresses.nonEmpty

//   def wrapClient(
//       client: SocketIOClient,
//       subscription: SocketIOSubscription
//     ) =
//     subscription.paramsForOrders.map { params =>
//       new SocketIOSubscriber(
//         client,
//         params.copy(
//           addresses = params.addresses.map(Address.normalize),
//           market = params.market.map(
//             market =>
//               market.copy(
//                 baseToken = Address.normalize(market.baseToken),
//                 quoteToken = Address.normalize(market.quoteToken)
//               )
//           )
//         )
//       )
//     }.get

//   def shouldNotifyClient(
//       subscription: SocketIOSubscription.ParamsForOrders,
//       event: SocketIOSubscription.Notification
//     ): Boolean = event.resForOrder match {
//     case Some(order: RawOrder) =>
//       subscription.addresses
//         .contains(order.owner) && (subscription.market.isEmpty || MarketHash(
//         subscription.getMarket
//       ) == MarketHash(MarketPair(order.tokenB, order.tokenS)))
//     case _ => false
//   }

// }
