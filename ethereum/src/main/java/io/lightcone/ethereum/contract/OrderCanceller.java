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
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
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
public class OrderCanceller extends Contract {
  private static final String BINARY =
      "608060405260008054600160a060020a031916905534801561002057600080fd5b50604051602080610b838339810180604052602081101561004057600080fd5b505160408051808201909152600c81527f5a45524f5f4144445245535300000000000000000000000000000000000000006020820152600160a060020a0382161515610124576040517f08c379a00000000000000000000000000000000000000000000000000000000081526004018080602001828103825283818151815260200191508051906020019080838360005b838110156100e95781810151838201526020016100d1565b50505050905090810190601f1680156101165780820380516001836020036101000a031916815260200191505b509250505060405180910390fd5b5060008054600160a060020a03909216600160a060020a0319909216919091179055610a2e806101556000396000f3fe608060405260043610610071577c0100000000000000000000000000000000000000000000000000000000600035046312f510f2811461014157806322baa82614610172578063543b5fa4146101bd578063a383de3a146101f6578063bd545f5314610273578063eac5c1901461029d575b604080518082018252600b81527f554e535550504f52544544000000000000000000000000000000000000000000602080830191825292517f08c379a00000000000000000000000000000000000000000000000000000000081526004810193845282516024820152825192939283926044909201919080838360005b838110156101065781810151838201526020016100ee565b50505050905090810190601f1680156101335780820380516001836020036101000a031916815260200191505b509250505060405180910390fd5b34801561014d57600080fd5b506101566102e0565b60408051600160a060020a039092168252519081900360200190f35b34801561017e57600080fd5b506101bb6004803603608081101561019557600080fd5b50600160a060020a038135811691602081013582169160408201351690606001356102ef565b005b3480156101c957600080fd5b506101bb600480360360408110156101e057600080fd5b50600160a060020a038135169060200135610411565b34801561020257600080fd5b506101bb6004803603602081101561021957600080fd5b81019060208101813564010000000081111561023457600080fd5b82018360208201111561024657600080fd5b8035906020019184600183028401116401000000008311171561026857600080fd5b5090925090506104f2565b34801561027f57600080fd5b506101bb6004803603602081101561029657600080fd5b503561078c565b3480156102a957600080fd5b506101bb600480360360608110156102c057600080fd5b50600160a060020a0381358116916020810135909116906040013561085b565b600054600160a060020a031681565b600081156102fd57816102ff565b425b60008054604080517ff732282b000000000000000000000000000000000000000000000000000000008152336004820152600160a060020a038a811660248301526c01000000000000000000000000898102908b02186bffffffffffffffffffffffff19811660448401526064830187905292519596509194919092169263f732282b926084808201939182900301818387803b15801561039f57600080fd5b505af11580156103b3573d6000803e3d6000fd5b505060408051600160a060020a03898116825288811660208301528183018790529151918a1693503392507f97bda78b7834715d21505da9c8801f7528412b8b3a6d49e6acad56a0ca9c5086919081900360600190a3505050505050565b6000811561041f5781610421565b425b60008054604080517f9bdbf652000000000000000000000000000000000000000000000000000000008152336004820152600160a060020a038881166024830152604482018690529151949550911692639bdbf6529260648084019391929182900301818387803b15801561049557600080fd5b505af11580156104a9573d6000803e3d6000fd5b5050604080518481529051600160a060020a03871693503392507f6c1800f274202aaab5d7cdb6ef958ce1c3a6ffe82696c4a872f11547485e36c49181900360200190a3505050565b80600081118015610504575060208106155b60408051808201909152600c81527f494e56414c49445f53495a45000000000000000000000000000000000000000060208201529015156105a1576040517f08c379a0000000000000000000000000000000000000000000000000000000008152600401808060200182810382528381815181526020019150805190602001908083836000838110156101065781810151838201526020016100ee565b506020810490506060816040519080825280602002602001820160405280156105d4578160200160208202803883390190505b5060008054919250600160a060020a03909116905b838110156107105761063d8160200287878080601f016020809104026020016040519081016040528093929190818152602001838380828437600092019190915250929392505063ffffffff61096d169050565b838281518110151561064b57fe5b906020019060200201818152505081600160a060020a031663cfd854c233858481518110151561067757fe5b906020019060200201516040518363ffffffff167c01000000000000000000000000000000000000000000000000000000000281526004018083600160a060020a0316600160a060020a0316815260200182815260200192505050600060405180830381600087803b1580156106ec57600080fd5b505af1158015610700573d6000803e3d6000fd5b5050600190920191506105e99050565b50604080516020808252845181830152845133937fa4f958623cd4c90b4a1213ca9f26b442398e4efad078048b6730c826fa4e5da193879390928392830191808601910280838360005b8381101561077257818101518382015260200161075a565b505050509050019250505060405180910390a25050505050565b6000811561079a578161079c565b425b60008054604080517f05e45560000000000000000000000000000000000000000000000000000000008152336004820152602481018590529051939450600160a060020a03909116926305e455609260448084019391929182900301818387803b15801561080957600080fd5b505af115801561081d573d6000803e3d6000fd5b50506040805184815290513393507f83a782ac7424737a1190d4668474e765f07d603de0485a081dbc343ac1b0209992509081900360200190a25050565b60008115610869578161086b565b425b60008054604080517fcb23e8990000000000000000000000000000000000000000000000000000000081523360048201526c01000000000000000000000000888102908a02186bffffffffffffffffffffffff19811660248301526044820186905291519495509093600160a060020a039092169263cb23e8999260648084019382900301818387803b15801561090157600080fd5b505af1158015610915573d6000803e3d6000fd5b505060408051600160a060020a0389811682528816602082015280820186905290513393507f03010b5cc0bf0153a225153a75169278a98eb21343f06fbff672a758a85b64a692509081900360600190a25050505050565b600061097b83836020610982565b9392505050565b60008183018451101515156109f857604080517f08c379a000000000000000000000000000000000000000000000000000000000815260206004820152600c60248201527f494e56414c49445f53495a450000000000000000000000000000000000000000604482015290519081900360640190fd5b509190910101519056fea165627a7a72305820e5ad8ceb70d91ed6b6730a1c6f6b62479fd10ee9d5715fc9e89dd9485c23bec00029";

  public static final String FUNC_TRADEHISTORYADDRESS = "tradeHistoryAddress";

  public static final String FUNC_CANCELALLORDERSFORTRADINGPAIROFOWNER =
      "cancelAllOrdersForTradingPairOfOwner";

  public static final String FUNC_CANCELALLORDERSOFOWNER = "cancelAllOrdersOfOwner";

  public static final String FUNC_CANCELORDERS = "cancelOrders";

  public static final String FUNC_CANCELALLORDERS = "cancelAllOrders";

  public static final String FUNC_CANCELALLORDERSFORTRADINGPAIR = "cancelAllOrdersForTradingPair";

  public static final Event ORDERSCANCELLED_EVENT =
      new Event(
          "OrdersCancelled",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Address>(true) {}, new TypeReference<DynamicArray<Bytes32>>() {}));;

  public static final Event ALLORDERSCANCELLEDFORTRADINGPAIR_EVENT =
      new Event(
          "AllOrdersCancelledForTradingPair",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Address>(true) {},
              new TypeReference<Address>() {},
              new TypeReference<Address>() {},
              new TypeReference<Uint256>() {}));;

  public static final Event ALLORDERSCANCELLED_EVENT =
      new Event(
          "AllOrdersCancelled",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));;

  public static final Event ALLORDERSCANCELLEDFORTRADINGPAIRBYBROKER_EVENT =
      new Event(
          "AllOrdersCancelledForTradingPairByBroker",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Address>(true) {},
              new TypeReference<Address>(true) {},
              new TypeReference<Address>() {},
              new TypeReference<Address>() {},
              new TypeReference<Uint256>() {}));;

  public static final Event ALLORDERSCANCELLEDBYBROKER_EVENT =
      new Event(
          "AllOrdersCancelledByBroker",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<Address>(true) {},
              new TypeReference<Address>(true) {},
              new TypeReference<Uint256>() {}));;

  @Deprecated
  protected OrderCanceller(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  protected OrderCanceller(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
  }

  @Deprecated
  protected OrderCanceller(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  protected OrderCanceller(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public RemoteCall<String> tradeHistoryAddress() {
    final Function function =
        new Function(
            FUNC_TRADEHISTORYADDRESS,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
    return executeRemoteCallSingleValueReturn(function, String.class);
  }

  public RemoteCall<TransactionReceipt> cancelAllOrdersForTradingPairOfOwner(
      String owner, String token1, String token2, BigInteger cutoff) {
    final Function function =
        new Function(
            FUNC_CANCELALLORDERSFORTRADINGPAIROFOWNER,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.Address(owner),
                new org.web3j.abi.datatypes.Address(token1),
                new org.web3j.abi.datatypes.Address(token2),
                new org.web3j.abi.datatypes.generated.Uint256(cutoff)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteCall<TransactionReceipt> cancelAllOrdersOfOwner(String owner, BigInteger cutoff) {
    final Function function =
        new Function(
            FUNC_CANCELALLORDERSOFOWNER,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.Address(owner),
                new org.web3j.abi.datatypes.generated.Uint256(cutoff)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteCall<TransactionReceipt> cancelOrders(byte[] orderHashes) {
    final Function function =
        new Function(
            FUNC_CANCELORDERS,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicBytes(orderHashes)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteCall<TransactionReceipt> cancelAllOrders(BigInteger cutoff) {
    final Function function =
        new Function(
            FUNC_CANCELALLORDERS,
            Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(cutoff)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public RemoteCall<TransactionReceipt> cancelAllOrdersForTradingPair(
      String token1, String token2, BigInteger cutoff) {
    final Function function =
        new Function(
            FUNC_CANCELALLORDERSFORTRADINGPAIR,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.Address(token1),
                new org.web3j.abi.datatypes.Address(token2),
                new org.web3j.abi.datatypes.generated.Uint256(cutoff)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function);
  }

  public List<OrdersCancelledEventResponse> getOrdersCancelledEvents(
      TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(ORDERSCANCELLED_EVENT, transactionReceipt);
    ArrayList<OrdersCancelledEventResponse> responses =
        new ArrayList<OrdersCancelledEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      OrdersCancelledEventResponse typedResponse = new OrdersCancelledEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse._broker = (String) eventValues.getIndexedValues().get(0).getValue();
      typedResponse._orderHashes =
          (List<byte[]>) eventValues.getNonIndexedValues().get(0).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<OrdersCancelledEventResponse> ordersCancelledEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<Log, OrdersCancelledEventResponse>() {
              @Override
              public OrdersCancelledEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(ORDERSCANCELLED_EVENT, log);
                OrdersCancelledEventResponse typedResponse = new OrdersCancelledEventResponse();
                typedResponse.log = log;
                typedResponse._broker = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse._orderHashes =
                    (List<byte[]>) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<OrdersCancelledEventResponse> ordersCancelledEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(ORDERSCANCELLED_EVENT));
    return ordersCancelledEventFlowable(filter);
  }

  public List<AllOrdersCancelledForTradingPairEventResponse>
      getAllOrdersCancelledForTradingPairEvents(TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(ALLORDERSCANCELLEDFORTRADINGPAIR_EVENT, transactionReceipt);
    ArrayList<AllOrdersCancelledForTradingPairEventResponse> responses =
        new ArrayList<AllOrdersCancelledForTradingPairEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      AllOrdersCancelledForTradingPairEventResponse typedResponse =
          new AllOrdersCancelledForTradingPairEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse._broker = (String) eventValues.getIndexedValues().get(0).getValue();
      typedResponse._token1 = (String) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse._token2 = (String) eventValues.getNonIndexedValues().get(1).getValue();
      typedResponse._cutoff = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<AllOrdersCancelledForTradingPairEventResponse>
      allOrdersCancelledForTradingPairEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<
                Log, AllOrdersCancelledForTradingPairEventResponse>() {
              @Override
              public AllOrdersCancelledForTradingPairEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(ALLORDERSCANCELLEDFORTRADINGPAIR_EVENT, log);
                AllOrdersCancelledForTradingPairEventResponse typedResponse =
                    new AllOrdersCancelledForTradingPairEventResponse();
                typedResponse.log = log;
                typedResponse._broker = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse._token1 =
                    (String) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse._token2 =
                    (String) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse._cutoff =
                    (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<AllOrdersCancelledForTradingPairEventResponse>
      allOrdersCancelledForTradingPairEventFlowable(
          DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(ALLORDERSCANCELLEDFORTRADINGPAIR_EVENT));
    return allOrdersCancelledForTradingPairEventFlowable(filter);
  }

  public List<AllOrdersCancelledEventResponse> getAllOrdersCancelledEvents(
      TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(ALLORDERSCANCELLED_EVENT, transactionReceipt);
    ArrayList<AllOrdersCancelledEventResponse> responses =
        new ArrayList<AllOrdersCancelledEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      AllOrdersCancelledEventResponse typedResponse = new AllOrdersCancelledEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse._broker = (String) eventValues.getIndexedValues().get(0).getValue();
      typedResponse._cutoff = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<AllOrdersCancelledEventResponse> allOrdersCancelledEventFlowable(
      EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<Log, AllOrdersCancelledEventResponse>() {
              @Override
              public AllOrdersCancelledEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(ALLORDERSCANCELLED_EVENT, log);
                AllOrdersCancelledEventResponse typedResponse =
                    new AllOrdersCancelledEventResponse();
                typedResponse.log = log;
                typedResponse._broker = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse._cutoff =
                    (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<AllOrdersCancelledEventResponse> allOrdersCancelledEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(ALLORDERSCANCELLED_EVENT));
    return allOrdersCancelledEventFlowable(filter);
  }

  public List<AllOrdersCancelledForTradingPairByBrokerEventResponse>
      getAllOrdersCancelledForTradingPairByBrokerEvents(TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(
            ALLORDERSCANCELLEDFORTRADINGPAIRBYBROKER_EVENT, transactionReceipt);
    ArrayList<AllOrdersCancelledForTradingPairByBrokerEventResponse> responses =
        new ArrayList<AllOrdersCancelledForTradingPairByBrokerEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      AllOrdersCancelledForTradingPairByBrokerEventResponse typedResponse =
          new AllOrdersCancelledForTradingPairByBrokerEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse._broker = (String) eventValues.getIndexedValues().get(0).getValue();
      typedResponse._owner = (String) eventValues.getIndexedValues().get(1).getValue();
      typedResponse._token1 = (String) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse._token2 = (String) eventValues.getNonIndexedValues().get(1).getValue();
      typedResponse._cutoff = (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<AllOrdersCancelledForTradingPairByBrokerEventResponse>
      allOrdersCancelledForTradingPairByBrokerEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<
                Log, AllOrdersCancelledForTradingPairByBrokerEventResponse>() {
              @Override
              public AllOrdersCancelledForTradingPairByBrokerEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(
                        ALLORDERSCANCELLEDFORTRADINGPAIRBYBROKER_EVENT, log);
                AllOrdersCancelledForTradingPairByBrokerEventResponse typedResponse =
                    new AllOrdersCancelledForTradingPairByBrokerEventResponse();
                typedResponse.log = log;
                typedResponse._broker = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse._owner = (String) eventValues.getIndexedValues().get(1).getValue();
                typedResponse._token1 =
                    (String) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse._token2 =
                    (String) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse._cutoff =
                    (BigInteger) eventValues.getNonIndexedValues().get(2).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<AllOrdersCancelledForTradingPairByBrokerEventResponse>
      allOrdersCancelledForTradingPairByBrokerEventFlowable(
          DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(ALLORDERSCANCELLEDFORTRADINGPAIRBYBROKER_EVENT));
    return allOrdersCancelledForTradingPairByBrokerEventFlowable(filter);
  }

  public List<AllOrdersCancelledByBrokerEventResponse> getAllOrdersCancelledByBrokerEvents(
      TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(ALLORDERSCANCELLEDBYBROKER_EVENT, transactionReceipt);
    ArrayList<AllOrdersCancelledByBrokerEventResponse> responses =
        new ArrayList<AllOrdersCancelledByBrokerEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      AllOrdersCancelledByBrokerEventResponse typedResponse =
          new AllOrdersCancelledByBrokerEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse._broker = (String) eventValues.getIndexedValues().get(0).getValue();
      typedResponse._owner = (String) eventValues.getIndexedValues().get(1).getValue();
      typedResponse._cutoff = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<AllOrdersCancelledByBrokerEventResponse> allOrdersCancelledByBrokerEventFlowable(
      EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new io.reactivex.functions.Function<Log, AllOrdersCancelledByBrokerEventResponse>() {
              @Override
              public AllOrdersCancelledByBrokerEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(ALLORDERSCANCELLEDBYBROKER_EVENT, log);
                AllOrdersCancelledByBrokerEventResponse typedResponse =
                    new AllOrdersCancelledByBrokerEventResponse();
                typedResponse.log = log;
                typedResponse._broker = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse._owner = (String) eventValues.getIndexedValues().get(1).getValue();
                typedResponse._cutoff =
                    (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<AllOrdersCancelledByBrokerEventResponse> allOrdersCancelledByBrokerEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(ALLORDERSCANCELLEDBYBROKER_EVENT));
    return allOrdersCancelledByBrokerEventFlowable(filter);
  }

  @Deprecated
  public static OrderCanceller load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new OrderCanceller(contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  @Deprecated
  public static OrderCanceller load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new OrderCanceller(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public static OrderCanceller load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    return new OrderCanceller(contractAddress, web3j, credentials, contractGasProvider);
  }

  public static OrderCanceller load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    return new OrderCanceller(contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public static RemoteCall<OrderCanceller> deploy(
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider,
      String _tradeHistoryAddress) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(_tradeHistoryAddress)));
    return deployRemoteCall(
        OrderCanceller.class, web3j, credentials, contractGasProvider, BINARY, encodedConstructor);
  }

  public static RemoteCall<OrderCanceller> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider,
      String _tradeHistoryAddress) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(_tradeHistoryAddress)));
    return deployRemoteCall(
        OrderCanceller.class,
        web3j,
        transactionManager,
        contractGasProvider,
        BINARY,
        encodedConstructor);
  }

  @Deprecated
  public static RemoteCall<OrderCanceller> deploy(
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit,
      String _tradeHistoryAddress) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(_tradeHistoryAddress)));
    return deployRemoteCall(
        OrderCanceller.class, web3j, credentials, gasPrice, gasLimit, BINARY, encodedConstructor);
  }

  @Deprecated
  public static RemoteCall<OrderCanceller> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit,
      String _tradeHistoryAddress) {
    String encodedConstructor =
        FunctionEncoder.encodeConstructor(
            Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(_tradeHistoryAddress)));
    return deployRemoteCall(
        OrderCanceller.class,
        web3j,
        transactionManager,
        gasPrice,
        gasLimit,
        BINARY,
        encodedConstructor);
  }

  public static class OrdersCancelledEventResponse {
    public Log log;

    public String _broker;

    public List<byte[]> _orderHashes;
  }

  public static class AllOrdersCancelledForTradingPairEventResponse {
    public Log log;

    public String _broker;

    public String _token1;

    public String _token2;

    public BigInteger _cutoff;
  }

  public static class AllOrdersCancelledEventResponse {
    public Log log;

    public String _broker;

    public BigInteger _cutoff;
  }

  public static class AllOrdersCancelledForTradingPairByBrokerEventResponse {
    public Log log;

    public String _broker;

    public String _owner;

    public String _token1;

    public String _token2;

    public BigInteger _cutoff;
  }

  public static class AllOrdersCancelledByBrokerEventResponse {
    public Log log;

    public String _broker;

    public String _owner;

    public BigInteger _cutoff;
  }
}
