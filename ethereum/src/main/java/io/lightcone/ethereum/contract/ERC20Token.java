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

package io.lightcone.ethereum.contract;

import io.reactivex.Flowable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * Auto generated code.
 *
 * <p><strong>Do not modify!</strong>
 *
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the <a
 * href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 4.0.1.
 */
public class ERC20Token extends Contract {
  private static final String BINARY =
      "60806040523480156200001157600080fd5b506040516200156938038062001569833981018060405260a08110156200003757600080fd5b8101908080516401000000008111156200005057600080fd5b820160208101848111156200006457600080fd5b81516401000000008111828201871017156200007f57600080fd5b505092919060200180516401000000008111156200009c57600080fd5b82016020810184811115620000b057600080fd5b8151640100000000811182820187101715620000cb57600080fd5b5050602082015160408301516060909301519194509250600082116200015257604080517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152600d60248201527f494e56414c49445f56414c554500000000000000000000000000000000000000604482015290519081900360640190fd5b600160a060020a0381161515620001ca57604080517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152600c60248201527f5a45524f5f414444524553530000000000000000000000000000000000000000604482015290519081900360640190fd5b620001df848664010000000062000248810204565b8451620001f4906000906020880190620008d5565b5083516200020a906001906020870190620008d5565b506002805460ff191660ff94909416939093179092556003819055600160a060020a03909116600090815260046020526040902055506200097a9050565b815182906003118015906200025f57506008815111155b1515620002cd57604080517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152600c60248201527f494e56414c49445f53495a450000000000000000000000000000000000000000604482015290519081900360640190fd5b60005b8151811015620006ab578181815181101515620002e957fe5b9060200101517f010000000000000000000000000000000000000000000000000000000000000090047f010000000000000000000000000000000000000000000000000000000000000002600160f860020a031916602e7f0100000000000000000000000000000000000000000000000000000000000000021480620003f4575081818151811015156200037957fe5b9060200101517f010000000000000000000000000000000000000000000000000000000000000090047f010000000000000000000000000000000000000000000000000000000000000002600160f860020a031916605f7f010000000000000000000000000000000000000000000000000000000000000002145b8062000514575081517f4100000000000000000000000000000000000000000000000000000000000000908390839081106200042c57fe5b9060200101517f010000000000000000000000000000000000000000000000000000000000000090047f010000000000000000000000000000000000000000000000000000000000000002600160f860020a0319161015801562000514575081517f5a0000000000000000000000000000000000000000000000000000000000000090839083908110620004bc57fe5b9060200101517f010000000000000000000000000000000000000000000000000000000000000090047f010000000000000000000000000000000000000000000000000000000000000002600160f860020a03191611155b8062000634575081517f6100000000000000000000000000000000000000000000000000000000000000908390839081106200054c57fe5b9060200101517f010000000000000000000000000000000000000000000000000000000000000090047f010000000000000000000000000000000000000000000000000000000000000002600160f860020a0319161015801562000634575081517f7a0000000000000000000000000000000000000000000000000000000000000090839083908110620005dc57fe5b9060200101517f010000000000000000000000000000000000000000000000000000000000000090047f010000000000000000000000000000000000000000000000000000000000000002600160f860020a03191611155b1515620006a257604080517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152600d60248201527f494e56414c49445f56414c554500000000000000000000000000000000000000604482015290519081900360640190fd5b600101620002d0565b5080518251839111801590620006c357506080815111155b15156200073157604080517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152600c60248201527f494e56414c49445f53495a450000000000000000000000000000000000000000604482015290519081900360640190fd5b60005b8151811015620008ce5781517f2000000000000000000000000000000000000000000000000000000000000000908390839081106200076f57fe5b9060200101517f010000000000000000000000000000000000000000000000000000000000000090047f010000000000000000000000000000000000000000000000000000000000000002600160f860020a0319161015801562000857575081517f7e0000000000000000000000000000000000000000000000000000000000000090839083908110620007ff57fe5b9060200101517f010000000000000000000000000000000000000000000000000000000000000090047f010000000000000000000000000000000000000000000000000000000000000002600160f860020a03191611155b1515620008c557604080517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152600d60248201527f494e56414c49445f56414c554500000000000000000000000000000000000000604482015290519081900360640190fd5b60010162000734565b5050505050565b828054600181600116156101000203166002900490600052602060002090601f016020900481019282601f106200091857805160ff191683800117855562000948565b8280016001018555821562000948579182015b82811115620009485782518255916020019190600101906200092b565b50620009569291506200095a565b5090565b6200097791905b8082111562000956576000815560010162000961565b90565b610bdf806200098a6000396000f3fe6080604052600436106100c4576000357c010000000000000000000000000000000000000000000000000000000090048063661884631161008157806366188463146102fe57806370a082311461033757806395d89b411461036a578063a9059cbb1461037f578063d73dd623146103b8578063dd62ed3e146103f1576100c4565b806306fdde031461017d578063095ea7b31461020757806318160ddd1461025457806323b872dd1461027b578063313ce567146102be578063324536eb146102e9575b604080518082018252600b81527f554e535550504f525445440000000000000000000000000000000000000000006020808301918252925160e560020a62461bcd0281526004810193845282516024820152825192939283926044909201919080838360005b8381101561014257818101518382015260200161012a565b50505050905090810190601f16801561016f5780820380516001836020036101000a031916815260200191505b509250505060405180910390fd5b34801561018957600080fd5b5061019261042c565b6040805160208082528351818301528351919283929083019185019080838360005b838110156101cc5781810151838201526020016101b4565b50505050905090810190601f1680156101f95780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b34801561021357600080fd5b506102406004803603604081101561022a57600080fd5b50600160a060020a0381351690602001356104ba565b604080519115158252519081900360200190f35b34801561026057600080fd5b50610269610521565b60408051918252519081900360200190f35b34801561028757600080fd5b506102406004803603606081101561029e57600080fd5b50600160a060020a03813581169160208101359091169060400135610527565b3480156102ca57600080fd5b506102d361075d565b6040805160ff9092168252519081900360200190f35b3480156102f557600080fd5b50610269610766565b34801561030a57600080fd5b506102406004803603604081101561032157600080fd5b50600160a060020a03813516906020013561076c565b34801561034357600080fd5b506102696004803603602081101561035a57600080fd5b5035600160a060020a031661085c565b34801561037657600080fd5b50610192610877565b34801561038b57600080fd5b50610240600480360360408110156103a257600080fd5b50600160a060020a0381351690602001356108d1565b3480156103c457600080fd5b50610240600480360360408110156103db57600080fd5b50600160a060020a038135169060200135610a38565b3480156103fd57600080fd5b506102696004803603604081101561041457600080fd5b50600160a060020a0381358116916020013516610ad1565b6000805460408051602060026001851615610100026000190190941693909304601f810184900484028201840190925281815292918301828280156104b25780601f10610487576101008083540402835291602001916104b2565b820191906000526020600020905b81548152906001019060200180831161049557829003601f168201915b505050505081565b336000818152600560209081526040808320600160a060020a038716808552908352818420869055815186815291519394909390927f8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925928290030190a35060015b92915050565b60035490565b6000600160a060020a0383161515610589576040805160e560020a62461bcd02815260206004820152600c60248201527f5a45524f5f414444524553530000000000000000000000000000000000000000604482015290519081900360640190fd5b600160a060020a0384166000908152600460205260409020548211156105e7576040805160e560020a62461bcd02815260206004820152600d6024820152600080516020610b94833981519152604482015290519081900360640190fd5b600160a060020a0384166000908152600560209081526040808320338452909152902054821115610650576040805160e560020a62461bcd02815260206004820152600d6024820152600080516020610b94833981519152604482015290519081900360640190fd5b600160a060020a038416600090815260046020526040902054610679908363ffffffff610afc16565b600160a060020a0380861660009081526004602052604080822093909355908516815220546106ae908363ffffffff610b4a16565b600160a060020a0380851660009081526004602090815260408083209490945591871681526005825282812033825290915220546106f2908363ffffffff610afc16565b600160a060020a03808616600081815260056020908152604080832033845282529182902094909455805186815290519287169391927fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef929181900390910190a35060019392505050565b60025460ff1681565b60035481565b336000908152600560209081526040808320600160a060020a0386168452909152812054808311156107c157336000908152600560209081526040808320600160a060020a03881684529091528120556107f6565b6107d1818463ffffffff610afc16565b336000908152600560209081526040808320600160a060020a03891684529091529020555b336000818152600560209081526040808320600160a060020a0389168085529083529281902054815190815290519293927f8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925929181900390910190a35060019392505050565b600160a060020a031660009081526004602052604090205490565b60018054604080516020600284861615610100026000190190941693909304601f810184900484028201840190925281815292918301828280156104b25780601f10610487576101008083540402835291602001916104b2565b6000600160a060020a0383161515610933576040805160e560020a62461bcd02815260206004820152600c60248201527f5a45524f5f414444524553530000000000000000000000000000000000000000604482015290519081900360640190fd5b33600090815260046020526040902054821115610988576040805160e560020a62461bcd02815260206004820152600d6024820152600080516020610b94833981519152604482015290519081900360640190fd5b336000908152600460205260409020546109a8908363ffffffff610afc16565b3360009081526004602052604080822092909255600160a060020a038516815220546109da908363ffffffff610b4a16565b600160a060020a0384166000818152600460209081526040918290209390935580518581529051919233927fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef9281900390910190a350600192915050565b336000908152600560209081526040808320600160a060020a0386168452909152812054610a6c908363ffffffff610b4a16565b336000818152600560209081526040808320600160a060020a0389168085529083529281902085905580519485525191937f8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925929081900390910190a350600192915050565b600160a060020a03918216600090815260056020908152604080832093909416825291909152205490565b600082821115610b44576040805160e560020a62461bcd02815260206004820152600d6024820152600080516020610b94833981519152604482015290519081900360640190fd5b50900390565b8181018281101561051b576040805160e560020a62461bcd02815260206004820152600d6024820152600080516020610b94833981519152604482015290519081900360640190fdfe494e56414c49445f56414c554500000000000000000000000000000000000000a165627a7a7230582076974945724ad3dd2024e3e088d8d72593595d5c2f9fd6f8e259fbaaea1bf6f70029";

  public static final String FUNC_NAME = "name";

  public static final String FUNC_APPROVE = "approve";

  public static final String FUNC_TOTALSUPPLY = "totalSupply";

  public static final String FUNC_TRANSFERFROM = "transferFrom";

  public static final String FUNC_DECIMALS = "decimals";

  public static final String FUNC_TOTALSUPPLY_ = "totalSupply_";

  public static final String FUNC_DECREASEAPPROVAL = "decreaseApproval";

  public static final String FUNC_BALANCEOF = "balanceOf";

  public static final String FUNC_SYMBOL = "symbol";

  public static final String FUNC_TRANSFER = "transfer";

  public static final String FUNC_INCREASEAPPROVAL = "increaseApproval";

  public static final String FUNC_ALLOWANCE = "allowance";

  public static final Event TRANSFER_EVENT =
      new Event(
          "Transfer",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Address>(true) {},
              new TypeReference<Address>(true) {},
              new TypeReference<Uint256>() {}));;

  public static final Event APPROVAL_EVENT =
      new Event(
          "Approval",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Address>(true) {},
              new TypeReference<Address>(true) {},
              new TypeReference<Uint256>() {}));;

  @Deprecated
  protected ERC20Token(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  protected ERC20Token(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
  }

  @Deprecated
  protected ERC20Token(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  protected ERC20Token(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public RemoteCall<String> name() {
    final Function function =
        new Function(
            FUNC_NAME,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}));
    return executeRemoteCallSingleValueReturn(function, String.class);
  }

  public RemoteCall<TransactionReceipt> approve(String _spender, BigInteger _value) {
    final Function function =
        new Function(
            FUNC_APPROVE,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.Address(_spender),
                new org.web3j.abi.datatypes.generated.Uint256(_value)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteCall<BigInteger> totalSupply() {
    final Function function =
        new Function(
            FUNC_TOTALSUPPLY,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public RemoteCall<TransactionReceipt> transferFrom(String _from, String _to, BigInteger _value) {
    final Function function =
        new Function(
            FUNC_TRANSFERFROM,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.Address(_from),
                new org.web3j.abi.datatypes.Address(_to),
                new org.web3j.abi.datatypes.generated.Uint256(_value)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteCall<BigInteger> decimals() {
    final Function function =
        new Function(
            FUNC_DECIMALS,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint8>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public RemoteCall<BigInteger> totalSupply_() {
    final Function function =
        new Function(
            FUNC_TOTALSUPPLY_,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public RemoteCall<TransactionReceipt> decreaseApproval(
      String _spender, BigInteger _subtractedValue) {
    final Function function =
        new Function(
            FUNC_DECREASEAPPROVAL,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.Address(_spender),
                new org.web3j.abi.datatypes.generated.Uint256(_subtractedValue)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteCall<BigInteger> balanceOf(String _owner) {
    final Function function =
        new Function(
            FUNC_BALANCEOF,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(_owner)),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public RemoteCall<String> symbol() {
    final Function function =
        new Function(
            FUNC_SYMBOL,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}));
    return executeRemoteCallSingleValueReturn(function, String.class);
  }

  public RemoteCall<TransactionReceipt> transfer(String _to, BigInteger _value) {
    final Function function =
        new Function(
            FUNC_TRANSFER,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.Address(_to),
                new org.web3j.abi.datatypes.generated.Uint256(_value)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteCall<TransactionReceipt> increaseApproval(String _spender, BigInteger _addedValue) {
    final Function function =
        new Function(
            FUNC_INCREASEAPPROVAL,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.Address(_spender),
                new org.web3j.abi.datatypes.generated.Uint256(_addedValue)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteCall<BigInteger> allowance(String _owner, String _spender) {
    final Function function =
        new Function(
            FUNC_ALLOWANCE,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.Address(_owner),
                new org.web3j.abi.datatypes.Address(_spender)),
            Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
    return executeRemoteCallSingleValueReturn(function, BigInteger.class);
  }

  public List<TransferEventResponse> getTransferEvents(TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(TRANSFER_EVENT, transactionReceipt);
    ArrayList<TransferEventResponse> responses =
        new ArrayList<TransferEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      TransferEventResponse typedResponse = new TransferEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse.from = (String) eventValues.getIndexedValues().get(0).getValue();
      typedResponse.to = (String) eventValues.getIndexedValues().get(1).getValue();
      typedResponse.value = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<TransferEventResponse> transferEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<Log, TransferEventResponse>() {
              @Override
              public TransferEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(TRANSFER_EVENT, log);
                TransferEventResponse typedResponse = new TransferEventResponse();
                typedResponse.log = log;
                typedResponse.from = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.to = (String) eventValues.getIndexedValues().get(1).getValue();
                typedResponse.value =
                    (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<TransferEventResponse> transferEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(TRANSFER_EVENT));
    return transferEventFlowable(filter);
  }

  public List<ApprovalEventResponse> getApprovalEvents(TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(APPROVAL_EVENT, transactionReceipt);
    ArrayList<ApprovalEventResponse> responses =
        new ArrayList<ApprovalEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      ApprovalEventResponse typedResponse = new ApprovalEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse.owner = (String) eventValues.getIndexedValues().get(0).getValue();
      typedResponse.spender = (String) eventValues.getIndexedValues().get(1).getValue();
      typedResponse.value = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<ApprovalEventResponse> approvalEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<Log, ApprovalEventResponse>() {
              @Override
              public ApprovalEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(APPROVAL_EVENT, log);
                ApprovalEventResponse typedResponse = new ApprovalEventResponse();
                typedResponse.log = log;
                typedResponse.owner = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.spender = (String) eventValues.getIndexedValues().get(1).getValue();
                typedResponse.value =
                    (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<ApprovalEventResponse> approvalEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(APPROVAL_EVENT));
    return approvalEventFlowable(filter);
  }

  @Deprecated
  public static ERC20Token load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new ERC20Token(contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  @Deprecated
  public static ERC20Token load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new ERC20Token(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public static ERC20Token load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    return new ERC20Token(contractAddress, web3j, credentials, contractGasProvider);
  }

  public static ERC20Token load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    return new ERC20Token(contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public static RemoteCall<ERC20Token> deploy(
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider,
      String _name,
      String _symbol,
      BigInteger _decimals,
      BigInteger _totalSupply,
      String _firstHolder) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.Utf8String(_name),
                new org.web3j.abi.datatypes.Utf8String(_symbol),
                new org.web3j.abi.datatypes.generated.Uint8(_decimals),
                new org.web3j.abi.datatypes.generated.Uint256(_totalSupply),
                new org.web3j.abi.datatypes.Address(_firstHolder)));
    return deployRemoteCall(
        ERC20Token.class, web3j, credentials, contractGasProvider, BINARY, encodedConstructor);
  }

  public static RemoteCall<ERC20Token> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider,
      String _name,
      String _symbol,
      BigInteger _decimals,
      BigInteger _totalSupply,
      String _firstHolder) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.Utf8String(_name),
                new org.web3j.abi.datatypes.Utf8String(_symbol),
                new org.web3j.abi.datatypes.generated.Uint8(_decimals),
                new org.web3j.abi.datatypes.generated.Uint256(_totalSupply),
                new org.web3j.abi.datatypes.Address(_firstHolder)));
    return deployRemoteCall(
        ERC20Token.class,
        web3j,
        transactionManager,
        contractGasProvider,
        BINARY,
        encodedConstructor);
  }

  @Deprecated
  public static RemoteCall<ERC20Token> deploy(
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit,
      String _name,
      String _symbol,
      BigInteger _decimals,
      BigInteger _totalSupply,
      String _firstHolder) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.Utf8String(_name),
                new org.web3j.abi.datatypes.Utf8String(_symbol),
                new org.web3j.abi.datatypes.generated.Uint8(_decimals),
                new org.web3j.abi.datatypes.generated.Uint256(_totalSupply),
                new org.web3j.abi.datatypes.Address(_firstHolder)));
    return deployRemoteCall(
        ERC20Token.class, web3j, credentials, gasPrice, gasLimit, BINARY, encodedConstructor);
  }

  @Deprecated
  public static RemoteCall<ERC20Token> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit,
      String _name,
      String _symbol,
      BigInteger _decimals,
      BigInteger _totalSupply,
      String _firstHolder) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.Utf8String(_name),
                new org.web3j.abi.datatypes.Utf8String(_symbol),
                new org.web3j.abi.datatypes.generated.Uint8(_decimals),
                new org.web3j.abi.datatypes.generated.Uint256(_totalSupply),
                new org.web3j.abi.datatypes.Address(_firstHolder)));
    return deployRemoteCall(
        ERC20Token.class,
        web3j,
        transactionManager,
        gasPrice,
        gasLimit,
        BINARY,
        encodedConstructor);
  }

  public static class TransferEventResponse {
    public Log log;

    public String from;

    public String to;

    public BigInteger value;
  }

  public static class ApprovalEventResponse {
    public Log log;

    public String owner;

    public String spender;

    public BigInteger value;
  }
}
