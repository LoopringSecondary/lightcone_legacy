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

// import com.corundumstudio.socketio._
// import com.google.inject.Inject
// import io.lightcone.lib.Address
// import io.lightcone.relayer.data._
// import io.lightcone.relayer.socketio._

// class AccountNotifier @Inject()
//     extends SocketIONotifier[SocketIOSubscription.ParamsForAccounts] {

//   val name = "accounts"

//   def isSubscriptionValid(subscription: SocketIOSubscription): Boolean =
//     subscription.paramsForAccounts.isDefined &&
//       subscription.getParamsForAccounts.addresses.nonEmpty

//   def wrapClient(
//       client: SocketIOClient,
//       req: SocketIOSubscription
//     ) =
//     req.paramsForAccounts
//       .map(
//         params =>
//           new SocketIOSubscriber(
//             client,
//             params.copy(
//               addresses = params.addresses.map(Address.normalize),
//               tokens = params.tokens.map(Address.normalize)
//             )
//           )
//       )
//       .get

//   def shouldNotifyClient(
//       subscription: SocketIOSubscription.ParamsForAccounts,
//       event: SocketIOSubscription.Notification
//     ): Boolean =
//     event.resForAccount match {
//       case Some(e: AccountUpdate) =>
//         subscription.addresses.contains(e.address) && (subscription.tokens.isEmpty || e.tokenBalance.isEmpty || subscription.tokens
//           .contains(e.getTokenBalance.token))
//       case _ => false
//     }
// }
