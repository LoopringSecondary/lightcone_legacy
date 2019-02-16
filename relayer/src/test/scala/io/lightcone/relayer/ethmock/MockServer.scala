/*

  Copyright 2017 Loopring Project Ltd (Loopring Foundation).

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

*/

package io.lightcone.relayer.ethmock

import akka.actor.{Actor, ActorRef}

/*
两种方式
1、通过模拟EtherHttpConnector实现，但是只能测试ethereum的部分功能，很多功能如网络连接、事件解析等无法测试到
2、通过模拟一个jsonrpc服务实现，可以模拟大部分功能
但是共同的无法处理的是，对于块的解析的处理

问题一：
目前以太坊完成的功能有，以太坊节点的维护和路由，请求数据，事件解析、事件的分发，
需要mock哪些内容
 */
object EthereumMock {

  val expectResults = Map.empty[String, Any]

  var mockActor: ActorRef = _

  def setResults(expects: Seq[()]) = {

  }

  def triggerEvent() = {

  }

  //start jsonrpc server
  def startEthService() = {
    //启动actor
    mockActor = null
    //启动service
  }

}

class MockActor
 extends Actor {

  //需要确切的知道有哪些请求，如提交一个订单会引起的可能请求：余额和授权：下单者的卖出和交易费和矿工的；订单CutOff情况；
  def receive: Receive = {
    case _ =>

  }

}

