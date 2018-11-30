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

package org.loopring.lightcone.ethereum.abi

import org.loopring.lightcone.ethereum.data._

import org.scalatest._

class ERC20ABISpec extends FlatSpec with Matchers {

  val erc20jsonstr = "[{\"constant\":false,\"inputs\":[{\"name\":\"spender\",\"type\":\"address\"},{\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"approve\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"totalSupply\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"tx_from\",\"type\":\"address\"},{\"name\":\"to\",\"type\":\"address\"},{\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"transferFrom\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"who\",\"type\":\"address\"}],\"name\":\"balanceOf\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"to\",\"type\":\"address\"},{\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"transfer\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"owner\",\"type\":\"address\"},{\"name\":\"spender\",\"type\":\"address\"}],\"name\":\"allowance\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"owner\",\"type\":\"address\"},{\"indexed\":true,\"name\":\"spender\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"Approval\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"tx_from\",\"type\":\"address\"},{\"indexed\":true,\"name\":\"to\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"Transfer\",\"type\":\"event\"}]"
  val wethjsonstr = "[{\"constant\":true,\"inputs\":[],\"name\":\"name\",\"outputs\":[{\"name\":\"\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"guy\",\"type\":\"address\"},{\"name\":\"wad\",\"type\":\"uint256\"}],\"name\":\"approve\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"totalSupply\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"src\",\"type\":\"address\"},{\"name\":\"dst\",\"type\":\"address\"},{\"name\":\"wad\",\"type\":\"uint256\"}],\"name\":\"transferFrom\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"wad\",\"type\":\"uint256\"}],\"name\":\"withdraw\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"decimals\",\"outputs\":[{\"name\":\"\",\"type\":\"uint8\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"address\"}],\"name\":\"balanceOf\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"symbol\",\"outputs\":[{\"name\":\"\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"dst\",\"type\":\"address\"},{\"name\":\"wad\",\"type\":\"uint256\"}],\"name\":\"transfer\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[],\"name\":\"deposit\",\"outputs\":[],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"address\"},{\"name\":\"\",\"type\":\"address\"}],\"name\":\"allowance\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"src\",\"type\":\"address\"},{\"indexed\":true,\"name\":\"guy\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"wad\",\"type\":\"uint256\"}],\"name\":\"Approval\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"src\",\"type\":\"address\"},{\"indexed\":true,\"name\":\"dst\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"wad\",\"type\":\"uint256\"}],\"name\":\"Transfer\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"dst\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"wad\",\"type\":\"uint256\"}],\"name\":\"Deposit\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"src\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"wad\",\"type\":\"uint256\"}],\"name\":\"Withdrawal\",\"type\":\"event\"}]"
  val erc20abi = new ERC20ABI(erc20jsonstr)
  val wethabi = new WethABI(wethjsonstr)

  "supported" should "get supported functions and events" in {
    info("[sbt lib/'testOnly *ERC20ABISpec -- -z supported']")

    erc20abi.supportedFunctions.foreach(x ⇒ info(x._1 + "    " + x._2.name))
    erc20abi.supportedEventLogs.foreach(x ⇒ info(x._1 + "    " + x._2.name))
  }

  "getFunction" should "get function correct" in {
    info("[sbt lib/'testOnly *ERC20ABISpec -- -z getFunction']")

    val transferInput = "0xa9059cbb000000000000000000000000e51ca63ffedede5eaf7e17db6dc51648af4edc3d00000000000000000000000000000000000000000000000eb66e7e0fa9040000"
    val function = erc20abi.getFunction(transferInput)

    function shouldNot be(empty)
    function.get.name should be("transfer")
  }

  "getEvent" should "get event correct" in {
    info("[sbt lib/'testOnly *ERC20ABISpec -- -z getEvent']")

    val topic = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
    val event = erc20abi.getEvent(topic)

    event shouldNot be(empty)
    event.get.name should be("Transfer")
  }

  "decodeTransferFunction" should "decode function input and assemble to class Transfer" in {
    info("[sbt lib/'testOnly *ERC20ABISpec -- -z decodeTransferFunction']")

    val from = "0x0681d8db095565fe8a346fa0277bffde9c0edbbf"
    val input = "0xa9059cbb000000000000000000000000f105c622edc68b9e4e813e631cb534940f5cc5090000000000000000000000000000000000000000000006425b02acb8d7bd0000"
    val tx = Transaction(from = from, input = input)
    val transfer = erc20abi.decodeAndAssemble(tx)

    transfer match {
      case Some(Transfer(sender, receiver, amount)) ⇒
        receiver should be("0xf105c622edc68b9e4e813e631cb534940f5cc509")
        amount.toString() should be("29558242000000000000000")

      case _ ⇒ true should be(false)
    }
  }

  "decodeTransferEvent" should "decode event data and assemble to class Transfer" in {
    info("[sbt lib/'testOnly *ERC20ABISpec -- -z decodeTransferEvent']")

    val from = "0x0681d8db095565fe8a346fa0277bffde9c0edbbf"
    val input = "0xa9059cbb000000000000000000000000f105c622edc68b9e4e813e631cb534940f5cc5090000000000000000000000000000000000000000000006425b02acb8d7bd0000"
    val data = "0x0000000000000000000000000000000000000000000006425b02acb8d7bd0000"
    val topics = Seq(
      "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
      "0x0000000000000000000000000681d8db095565fe8a346fa0277bffde9c0edbbf",
      "0x000000000000000000000000f105c622edc68b9e4e813e631cb534940f5cc509"
    )

    val tx = Transaction(from = from, input = input)
    val log = TransactionLog(data = data, topics = topics)

    val transfer = erc20abi.decodeAndAssemble(tx, log)

    transfer match {
      case Some(Transfer(sender, receiver, amount)) ⇒
        sender should be("0x0681d8db095565fe8a346fa0277bffde9c0edbbf")
        receiver should be("0xf105c622edc68b9e4e813e631cb534940f5cc509")
        amount.toString() should be("29558242000000000000000")

      case _ ⇒ true should be(false)
    }
  }

  "decodeApproveFunction" should "decode function input and assemble to class Approve" in {
    info("[sbt lib/'testOnly *ERC20ABISpec -- -z decodeApproveFunction']")

    val from = "0x85194623225c1a0576abf8e2bdc0951351fcddda"
    val input = "0x095ea7b30000000000000000000000008fd3121013a07c57f0d69646e86e7a4880b467b70000000000000000000000000000000000000000004a817c7ffffffb57e83800"
    val tx = Transaction(from = from, input = input)
    val approve = erc20abi.decodeAndAssemble(tx)

    approve match {
      case Some(Approve(owner, spender, amount)) ⇒
        spender should be("0x8fd3121013a07c57f0d69646e86e7a4880b467b7")
        amount.toString() should be("90071992547409900000000000")

      case _ ⇒ true should be(false)
    }
  }

  "decodeApproveEvent" should "decode event data and assemble to class Approve" in {
    info("[sbt lib/'testOnly *ERC20ABISpec -- -z decodeApproveEvent']")

    val from = "0x85194623225c1a0576abf8e2bdc0951351fcddda"
    val input = "0x095ea7b30000000000000000000000008fd3121013a07c57f0d69646e86e7a4880b467b70000000000000000000000000000000000000000004a817c7ffffffb57e83800"
    val data = "0x0000000000000000000000000000000000000000004a817c7ffffffb57e83800"
    val topics = Seq(
      "0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925",
      "0x00000000000000000000000085194623225c1a0576abf8e2bdc0951351fcddda",
      "0x0000000000000000000000008fd3121013a07c57f0d69646e86e7a4880b467b7"
    )

    val tx = Transaction(from = from, input = input)
    val log = TransactionLog(data = data, topics = topics)

    val approve = erc20abi.decodeAndAssemble(tx, log)

    approve match {
      case Some(Approve(owner, spender, amount)) ⇒
        owner should be("0x85194623225c1a0576abf8e2bdc0951351fcddda")
        spender should be("0x8fd3121013a07c57f0d69646e86e7a4880b467b7")
        amount.toString() should be("90071992547409900000000000")

      case _ ⇒ true should be(false)
    }
  }

  "decodeDepositFunction" should "decode function input and assemble to class Deposit" in {
    info("[sbt lib/'testOnly *ERC20ABISpec -- -z decodeDepositFunction']")

    val from = "0x9d30f9d302989ca1df6e4db8361fc2535997cfb7"
    val input = "0xd0e30db0"
    val value = "0x2386f26fc10000"
    val tx = Transaction(from = from, input = input, value = value)

    val deposit = wethabi.decodeAndAssemble(tx)
    deposit match {
      case Some(Deposit(owner, amount)) ⇒
        owner should be("0x9d30f9d302989ca1df6e4db8361fc2535997cfb7")
        amount.toString should be("10000000000000000")

      case _ ⇒ true should be(false)
    }
  }

  "decodeDepositEvent" should "decode event data and assemble to class Deposit" in {
    info("[sbt lib/'testOnly *ERC20ABISpec -- -z decodeDepositEvent']")

    val from = "0x9d30f9d302989ca1df6e4db8361fc2535997cfb7"
    val input = "0xd0e30db0"
    val value = "0x2386f26fc10000"
    val data = "0x000000000000000000000000000000000000000000000000002386f26fc10000"
    val topics = Seq(
      "0xe1fffcc4923d04b559f4d29a8bfc6cda04eb5b0d3c460751c2402c5c5cc9109c",
      "0x0000000000000000000000009d30f9d302989ca1df6e4db8361fc2535997cfb7"
    )
    val tx = Transaction(from = from, input = input, value = value)
    val log = TransactionLog(data = data, topics = topics)

    val deposit = wethabi.decodeAndAssemble(tx, log)
    deposit match {
      case Some(Deposit(owner, amount)) ⇒
        owner should be("0x9d30f9d302989ca1df6e4db8361fc2535997cfb7")
        amount.toString should be("10000000000000000")

      case _ ⇒ true should be(false)
    }
  }

  "decodeWithdrawalFunction" should "decode function input and assemble to class Withdrawal" in {
    info("[sbt lib/'testOnly *ERC20ABISpec -- -z decodeWithdrawalFunction']")

    val from = "0xda9bf8e3e67882ba59e291b6897e3db114cf6bde"
    val input = "0x2e1a7d4d000000000000000000000000000000000000000000000000001d18c2f9116d80"
    val tx = Transaction(from = from, input = input)

    val withdrawal = wethabi.decodeAndAssemble(tx)
    withdrawal match {
      case Some(Withdrawal(owner, amount)) ⇒
        owner should be("0xda9bf8e3e67882ba59e291b6897e3db114cf6bde")
        amount.toString should be("8190000006000000")

      case _ ⇒ true should be(false)
    }
  }

  "decodeWithdrawalEvent" should "decode event data and assemble to class Withdrawal" in {
    info("[sbt lib/'testOnly *ERC20ABISpec -- -z decodeWithdrawalEvent']")

    val from = "0xda9bf8e3e67882ba59e291b6897e3db114cf6bde"
    val input = "0x2e1a7d4d000000000000000000000000000000000000000000000000001d18c2f9116d80"
    val data = "0x000000000000000000000000000000000000000000000000001d18c2f9116d80"
    val topics = Seq(
      "0x7fcf532c15f0a6db0bd6d0e038bea71d30d808c7d98cb3bf7268a95bf5081b65",
      "0x000000000000000000000000da9bf8e3e67882ba59e291b6897e3db114cf6bde"
    )

    val tx = Transaction(from = from, input = input)
    val log = TransactionLog(data = data, topics = topics)

    val withdrawal = wethabi.decodeAndAssemble(tx)
    withdrawal match {
      case Some(Withdrawal(owner, amount)) ⇒
        owner should be("0xda9bf8e3e67882ba59e291b6897e3db114cf6bde")
        amount.toString should be("8190000006000000")

      case _ ⇒ true should be(false)
    }
  }
}
