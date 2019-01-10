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

import org.scalatest._
import org.web3j.utils.Numeric

class ERC20ABISpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  val erc20abi = ERC20ABI()

  override def beforeAll() {
    info(s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`")
  }

  "encodeTransferFunction" should "encode class Parms of transfer function to  input" in {
    val parms = TransferFunction.Parms(
      to = "0xf105c622edc68b9e4e813e631cb534940f5cc509",
      amount = BigInt("29558242000000000000000")
    )
    val input = erc20abi.transfer.pack(parms)
    info(input)
    input should be(
      "0xa9059cbb000000000000000000000000f105c622edc68b9e4e813e631cb534940f5cc5090000000000000000000000000000000000000000000006425b02acb8d7bd0000"
    )
  }

  "decodeTransferFunction" should "decode function input and assemble to class Transfer" in {
    val from = "0x0681d8db095565fe8a346fa0277bffde9c0edbbf"
    val input =
      "0xa9059cbb000000000000000000000000f105c622edc68b9e4e813e631cb534940f5cc5090000000000000000000000000000000000000000000006425b02acb8d7bd0000"
    val transfer = erc20abi.transfer.unpackInput(input)
    info(transfer.toString)
  }

  "decodeTransferEvent" should "decode event data and assemble to class Transfer" in {
    val from = "0x0681d8db095565fe8a346fa0277bffde9c0edbbf"
    val input = Numeric.hexStringToByteArray(
      "0xa9059cbb000000000000000000000000f105c622edc68b9e4e813e631cb534940f5cc5090000000000000000000000000000000000000000000006425b02acb8d7bd0000"
    )
    val data =
      "0x0000000000000000000000000000000000000000000006425b02acb8d7bd0000"
    val topics = Seq(
      "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
      "0x0000000000000000000000000681d8db095565fe8a346fa0277bffde9c0edbbf",
      "0x000000000000000000000000f105c622edc68b9e4e813e631cb534940f5cc509"
    )

    val transferOpt = erc20abi.transferEvent.unpack(data, topics.toArray)
    transferOpt match {
      case None =>
      case Some(transfer) =>
        transfer.sender should be("0x0681d8db095565fe8a346fa0277bffde9c0edbbf")
        transfer.receiver should be(
          "0xf105c622edc68b9e4e813e631cb534940f5cc509"
        )
        transfer.amount should be(BigInt("29558242000000000000000"))
    }
  }

  "encodeApproveFunction" should "encode class Parms of approve function to  input" in {
    val parms = ApproveFunction.Parms(
      spender = "0x8fd3121013a07c57f0d69646e86e7a4880b467b7",
      amount = BigInt("90071992547409900000000000")
    )
    val input = erc20abi.approve.pack(parms)
    info(input)
    input should be(
      "0x095ea7b30000000000000000000000008fd3121013a07c57f0d69646e86e7a4880b467b70000000000000000000000000000000000000000004a817c7ffffffb57e83800"
    )
  }

  //
  "decodeApproveFunction" should "decode function input and assemble to class Approve" in {
    val from = "0x85194623225c1a0576abf8e2bdc0951351fcddda"
    val input =
      "0x095ea7b30000000000000000000000008fd3121013a07c57f0d69646e86e7a4880b467b70000000000000000000000000000000000000000004a817c7ffffffb57e83800"
    val approveOpt = erc20abi.approve.unpackInput(input)

    approveOpt match {
      case None =>
      case Some(approve) =>
        approve.spender should be("0x8fd3121013a07c57f0d69646e86e7a4880b467b7")
        approve.amount should be(BigInt("90071992547409900000000000"))
    }
  }

  "encodeBalanceOfFunction" should "encode class Parmas of balanceOf function to  input" in {
    val parms = BalanceOfFunction.Parms(
      _owner = "0x8fd3121013a07c57f0d69646e86e7a4880b467b7"
    )

    val input = erc20abi.balanceOf.pack(parms)
    info(input)
    input should be(
      "0x70a082310000000000000000000000008fd3121013a07c57f0d69646e86e7a4880b467b7"
    )
  }

  "encodeAllowanceFunction" should "encode class Parmas of allowance function to  input" in {
    val parms = AllowanceFunction.Parms(
      _owner = "0x8fd3121013a07c57f0d69646e86e7a4880b467b7",
      _spender = "0xf105c622edc68b9e4e813e631cb534940f5cc509"
    )

    val input = erc20abi.allowance.pack(parms)
    info(input)
    input should be(
      "0xdd62ed3e0000000000000000000000008fd3121013a07c57f0d69646e86e7a4880b467b7000000000000000000000000f105c622edc68b9e4e813e631cb534940f5cc509"
    )
  }

  "decodeBalanceOfFunctionResult" should "decode eth call result to BalanceOfFunction Result" in {
    val resp =
      "0x0000000000000000000000000000000000000000004a817c7ffffffb57e83800"
    val result = erc20abi.balanceOf.unpackResult(resp)
    result.map { res =>
      res.balance.toString() should be("90071992547409900000000000")
    }
  }

  "decodeAllowanceFunctionResult" should "decode eth call result to AllowanceFunction Result" in {
    val resp =
      "0x0000000000000000000000000000000000000000004a817c7ffffffb57e83800"
    val result = erc20abi.allowance.unpackResult(resp)
    result.map { res =>
      res.allowance.toString() should be("90071992547409900000000000")
    }
  }

  "decodeApproveEvent" should "decode event data and assemble to class Approve" in {
    val from = "0x85194623225c1a0576abf8e2bdc0951351fcddda"
    val input =
      "0x095ea7b30000000000000000000000008fd3121013a07c57f0d69646e86e7a4880b467b70000000000000000000000000000000000000000004a817c7ffffffb57e83800"
    val data =
      "0x0000000000000000000000000000000000000000004a817c7ffffffb57e83800"
    val topics = Array(
      "0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925",
      "0x00000000000000000000000085194623225c1a0576abf8e2bdc0951351fcddda",
      "0x0000000000000000000000008fd3121013a07c57f0d69646e86e7a4880b467b7"
    )

    val approveOpt = erc20abi.approvalEvent.unpack(data, topics)

    approveOpt match {
      case Some(approve) =>
        approve.owner should be("0x85194623225c1a0576abf8e2bdc0951351fcddda")
        approve.spender should be("0x8fd3121013a07c57f0d69646e86e7a4880b467b7")
        approve.amount.toString() should be("90071992547409900000000000")
      case _ =>
    }
  }

  //decode Approval Event
  "decodeEventOfApproval" should "decode event data and assemble to class object" in {
    val from = "0x85194623225c1a0576abf8e2bdc0951351fcddda"
    val input =
      "0x095ea7b30000000000000000000000008fd3121013a07c57f0d69646e86e7a4880b467b70000000000000000000000000000000000000000004a817c7ffffffb57e83800"
    val data =
      "0x0000000000000000000000000000000000000000004a817c7ffffffb57e83800"
    val topics = Array(
      "0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925",
      "0x00000000000000000000000085194623225c1a0576abf8e2bdc0951351fcddda",
      "0x0000000000000000000000008fd3121013a07c57f0d69646e86e7a4880b467b7"
    )

    val eventOpt = erc20abi.unpackEvent(data, topics)
    eventOpt match {
      case Some(approve: ApprovalEvent.Result) =>
        approve.owner should be("0x85194623225c1a0576abf8e2bdc0951351fcddda")
        approve.spender should be("0x8fd3121013a07c57f0d69646e86e7a4880b467b7")
        approve.amount.toString() should be("90071992547409900000000000")
      case _ =>
    }
  }

  //decode Approve Function
  "decodeFunctionOfApprove" should "decode function input and assemble to class Object" in {
    val from = "0x85194623225c1a0576abf8e2bdc0951351fcddda"
    val input =
      "0x095ea7b30000000000000000000000008fd3121013a07c57f0d69646e86e7a4880b467b70000000000000000000000000000000000000000004a817c7ffffffb57e83800"
    val funcOpt = erc20abi.unpackFunctionInput(input)

    funcOpt match {
      case Some(approve: ApproveFunction.Parms) =>
        approve.spender should be("0x8fd3121013a07c57f0d69646e86e7a4880b467b7")
        approve.amount should be(BigInt("90071992547409900000000000"))
      case _ =>
    }
  }

  "decoderFunctionOfTransfer" should "decode function input and assemble to class Transfer" in {
    val from = "0x0681d8db095565fe8a346fa0277bffde9c0edbbf"
    val input =
      "0xa9059cbb000000000000000000000000f105c622edc68b9e4e813e631cb534940f5cc5090000000000000000000000000000000000000000000006425b02acb8d7bd0000"
    val transferOpt = erc20abi.unpackFunctionInput(input)
    info(transferOpt.toString)
    transferOpt match {
      case Some(tranfer: TransferFunction.Parms) =>
        tranfer.amount.toString() should be("29558242000000000000000")
        tranfer.to should be("0xf105c622edc68b9e4e813e631cb534940f5cc509")
      case _ =>
    }
  }

  "decodeEventOfTransfer" should "decode event data and assemble to class Transfer" in {
    val from = "0x0681d8db095565fe8a346fa0277bffde9c0edbbf"
    val input =
      "0xa9059cbb000000000000000000000000f105c622edc68b9e4e813e631cb534940f5cc5090000000000000000000000000000000000000000000006425b02acb8d7bd0000"
    val data =
      "0x0000000000000000000000000000000000000000000006425b02acb8d7bd0000"
    val topics = Seq(
      "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
      "0x0000000000000000000000000681d8db095565fe8a346fa0277bffde9c0edbbf",
      "0x000000000000000000000000f105c622edc68b9e4e813e631cb534940f5cc509"
    )

    val transferOpt = erc20abi.unpackEvent(data, topics.toArray)
    transferOpt match {
      case Some(transfer: TransferEvent.Result) =>
        transfer.sender should be("0x0681d8db095565fe8a346fa0277bffde9c0edbbf")
        transfer.receiver should be(
          "0xf105c622edc68b9e4e813e631cb534940f5cc509"
        )
        transfer.amount should be(BigInt("29558242000000000000000"))
      case _ =>
    }
  }

}
