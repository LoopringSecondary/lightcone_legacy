## lightcone relay 中文API 文档

### Order结构：

- owner :  Address
- version :  Hex String 
- tokenS : Address
- tokenB : Address
- amountS : Hex String 
- amountB : Hex String
- validSince : Hex String (seconds)
- params: 
  - broker: Address （可选）
  - dualAuthAddr:Address
  - dualAuthPrivateKey:Hex String
  - validUntil : Hex String (seconds，可选，不设置时代表该订单永远有效)  
  - allOrNone：Hex String 
  - wallet :Address  * 不确定含义
  - orderInterceptor: Address * 不确定含义
  - dualAuthSig: Hex String
  - sig : Hex String
- feeParams:
  - tokenRecipient:  Address（token 收款地址，一般为owner）
  - amountFee：Hex String
  - tokenFee：Address
  - waiveFeePercentaget : Hex String
  - tokenSFeePercentage : Hex String
  - tokenBFeePercentage:   Hex String
  - walletSplitPercentage:  Hex String
- ERC1400Params:
  - tokenStandardS : Hex String ("0x0" 代表 ERC20 ，"0x1"代表ERC1400)
  - tokenStandardB: Hex String  ("0x0" 代表 ERC20 ，"0x1"代表ERC1400)
  - tokenStandardFee : Hex String  ("0x0" 代表 ERC20 ，"0x1"代表ERC1400)
  - trancheS : Hex String    * 不确定含义
  - trancheS: Hex String    * 不确定含义
  - transferDataS : Hex String   * 不确定含义
- signType: Hex String  (0 代表Ethereum Sign ,1 代表EIP712签名方式)

### JSON-RPC 接口

#### 基础接口

- ##### getServerTime （待商议是不是需要）

  获取server 的时间戳，单位是毫秒。

  参数：

  ```json
  {
    "jsonrpc": "2.0",
    "method": "loopring_getServerTime",
    "params": [],
    "id": 1
  }
  ```

  返回：

  ```json
  {
    "id":1,
    "jsonrpc": "2.0",
    "result": 1548410119809
  }
  ```

- ##### getTokenMetaData 

  获取Relay支持交易的token的信息，包括最小下单量，最大下单量，保留小数位，地址, symbol,decimal等

  参数：

  ```json
  {
    "jsonrpc": "2.0",
    "method": "loopring_getTokenMetaData",
    "params": ["LRC","WETH"],
    "id": 1
  }
  ```

  若不指定任何token，则返回全部支持的token信息

  返回：

  ```json
  {
    "id": 1,
    "jsonrpc": "2.0",
    "result": [
      {
        "minTradeAmount": 0,
        "maxTradeAmount": 100000000,
        "precision": 5,
        "decimal": 18,
        "symbol": "LRC",
        "address": "0xef68e7c694f40c8202821edf525de3782458639f"
      },
      {
        "minTradeAmount": 0,
        "maxTradeAmount": 100000000,
        "precision": 5,
        "decimal": 18,
        "symbol": "WETH",
        "address": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"
      }
    ]
  }
  ```

- ##### getMarketMetaData

  获取Relay支持的市场信息。

  参数：

  ```json
  {
    "jsonrpc": "2.0",
    "method": "loopring_getMarketMetaData",
    "params": ["LRC-WETH"],
    "id": 1
  }
  ```

  若不指定任何market，则返回全部支持的market信息。

  返回：

  ```json
  {
    "id": 1,
    "jsonrpc": "2.0",
    "result": [
      {
        "symbol": "LRC-WETH",
        "precisionForAmount": 5,
        "precisionForTotal": 8,
        "precisionForPrice": 5
      }
    ]
  }
  ```

- ##### getAssets

  获取指定用户的指定token的余额和授权信息

  参数：

  ```json
  {
    "jsonrpc": "2.0",
    "method": "loopring_getAssets",
    "params": [
      {
        "owner": "0xb94065482ad64d4c2b9252358d746b39e820a582",
        "tokens": ["LRC"]
      }
    ],
    "id": 1
  }
  ```

  若token不指定则返回relay支持的全部token的余额和授权

  返回：

  ```json
  {
    "id": 1,
    "jsonrpc": "2.0",
    "result": {
      "owner": "0xb94065482ad64d4c2b9252358d746b39e820a582",
      "records": [
        {
          "symbol": "LRC",
          "balance": "0x1326beb03e0a0000",
          "allowance": "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        },
        ...
      ]
    }
  ```

- ##### submitOrder

  用户提交订单


  参数：

  ```json
  {
    "jsonrpc": "2.0",
    "method": "loopring_submitOrder",
    "params": [
      {
        "owner": "0xb94065482ad64d4c2b9252358d746b39e820a582",
        "version": "0x0",
        "tokenS": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
        "tokenB": "0xef68e7c694f40c8202821edf525de3782458639f",
        "amountS": "0xde0b6b3a7640000",
        "validSince": "0x5c4b0cb3",
        "amountB": "0x3635c9adc5dea00000",
        "params": {
          "validUnit": "0x5c4cacb3",
          "allOrNone": "0x0",
          "sig": "0xa3f20717a250c2b0b729b7e5becbff67fdaef7e0699da4de7ca5895b02a170a12d887fd3b17bfdce3481f10bea41f45ba9f709d39ce8325427b57afcfc994cee1b",
          "dualAuthAddr": "0x7ebdf3751f63a5fc1742ba98ee34392ce82fa8dd",
          "dualAuthPrivateKey": "0xc3d695ee4fcb7f14b8cf08a1d588736264ff0d34d6b9b0893a820fe01d1086a6"
        },
        "feeParams": {
          "tokenFee": "0xef68e7c694f40c8202821edf525de3782458639f",
          "amountFee": "0xde0b6b3a7640000"
        },
        "signType": "0x0"
      }
    ],
    "id": 1
  }
  ```

  返回：

  ```json
  {
    "id":1,
    "jsonrpc": "2.0",
    "result":"0x6661e9d6d8b923d5bbaab1b96e1dd51ff6ea2a93520fdc9eb75d059238b8c5e9"
  }
  ```

- ##### cancelOrder  

  --- 需要商量如何进行签名。第一版按照对时间戳进行签名。

  用户取消订单

  参数：

  ```json
  {
    "jsonrpc": "2.0",
    "method": "loopring_cancelOrder",
    "params": [
      "0xfff19b90ccf6eb051dd71aa29350acbcaedbbfb841e66c202fda5bf7bd084b85"
    ],
    "id": 1
  }
  ```

  返回:

  ```json
  {
    "id":1,
    "jsonrpc": "2.0",
    "result":"0xfff19b90ccf6eb051dd71aa29350acbcaedbbfb841e66c202fda5bf7bd084b85"
  }
  ```

- ##### cancelOrders

  用户批量取消订单

  参数：

  ```json
  {
    "jsonrpc": "2.0",
    "method": "loopring_cancelOrders",
    "params": [
      {
        "market": "LRC-WETH"
      }
    ],
    "id": 1
  }
  ```

  如果不指定market 则应该取消全部的订单

  返回：

  ```json
  {
     "id":1,
    "jsonrpc": "2.0",
    "result": 20 //代表被取消的订单数量
  }
  ```

- ##### getOrders

  获取用户的订单列表

  参数 ：

  ```json
  {
    "jsonrpc": "2.0",
    "method": "loopring_getOrders",
    "params": [
      {
        "owner": "0xb94065482ad64d4c2b9252358d746b39e820a582",
        "market": "LRC-WETH",
        "status": "0x0",
        "pageNum": 1,
        "pageSize": 50
      }
    ],
    "id": 1
  }
  ```

  status : "0x0" 代表pending，"0x1" 代表撮合完成， "0x2"代表取消  "0x3"代表失效过期的

  返回：

  ```json
  {
    "id": 1,
    "jsonrpc": "2.0",
    "result": {
      "pageNum": 1,
      "pageSize": 50,
      "total": 60,
      "records": [
        {
          {
            "owner": "0xb94065482ad64d4c2b9252358d746b39e820a582",
            "version": "0x0",
            "tokenS": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
            "tokenB": "0xef68e7c694f40c8202821edf525de3782458639f",
            "amountS": "0xde0b6b3a7640000",
            "validSince": "0x5c4b0cb3",
            "amountB": "0x3635c9adc5dea00000",
            "params": {
              "validUnit": "0x5c4cacb3",
              "allOrNone": "0x0",
              "sig": "0xa3f20717a250c2b0b729b7e5becbff67fdaef7e0699da4de7ca5895b02a170a12d887fd3b17bfdce3481f10bea41f45ba9f709d39ce8325427b57afcfc994cee1b",
              "dualAuthAddr": "0x7ebdf3751f63a5fc1742ba98ee34392ce82fa8dd",
              "dualAuthPrivateKey": "0xc3d695ee4fcb7f14b8cf08a1d588736264ff0d34d6b9b0893a820fe01d1086a6"
            },
            "feeParams": {
              "tokenFee": "0xef68e7c694f40c8202821edf525de3782458639f",
              "amountFee": "0xde0b6b3a7640000"
            },
            "signType": "0x0"
          }
        },
        ...
      ]
    }
  }
  ```

- ##### getOrderByHash

  根据OrderHash查询单条Order记录

  参数 ： 

  ```json
  {
    "jsonrpc": "2.0",
    "method": "loopring_getOrderByHash",
    "params": [
      "0xfff19b90ccf6eb051dd71aa29350acbcaedbbfb841e66c202fda5bf7bd084b85"
    ],
    "id": 1
  }
  ```

  返回：

  ```json
  {
    "id": 1,
    "jsonrpc": "2.0",
    "result": {
      {
        "owner": "0xb94065482ad64d4c2b9252358d746b39e820a582",
        "version": "0x0",
        "tokenS": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
        "tokenB": "0xef68e7c694f40c8202821edf525de3782458639f",
        "amountS": "0xde0b6b3a7640000",
        "validSince": "0x5c4b0cb3",
        "amountB": "0x3635c9adc5dea00000",
        "params": {
          "validUnit": "0x5c4cacb3",
          "allOrNone": "0x0",
          "sig": "0xa3f20717a250c2b0b729b7e5becbff67fdaef7e0699da4de7ca5895b02a170a12d887fd3b17bfdce3481f10bea41f45ba9f709d39ce8325427b57afcfc994cee1b",
          "dualAuthAddr": "0x7ebdf3751f63a5fc1742ba98ee34392ce82fa8dd",
          "dualAuthPrivateKey": "0xc3d695ee4fcb7f14b8cf08a1d588736264ff0d34d6b9b0893a820fe01d1086a6"
        },
        "feeParams": {
          "tokenFee": "0xef68e7c694f40c8202821edf525de3782458639f",
          "amountFee": "0xde0b6b3a7640000"
        },
        "signType": "0x0"
      }
    }
  }
  ```

- ##### getTrades （目前还没有实现）

  获取用户的成交记录

  参数：

  ```json
  {
    "jsonrpc": "2.0",
    "method": "loopring_getTrades",
    "params": [
      {
        "owner": "0xb94065482ad64d4c2b9252358d746b39e820a582",
        "market": "LRC-WETH",
        "pageNum": 1,
        "pageSize": 50
      }
    ],
    "id": 1
  }
  ```

  返回：

  ```json
  {
    "id": 1,
    "jsonrpc": "2.0",
    "result": {
      "pageNum": 1,
      "pageSize": 50,
      "total": 60,
      "records": [
        {
          "orderHash": "",
          "ringHash": "",
          "tokenS": "0xef68e7c694f40c8202821edf525de3782458639f",
          "tokenB": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
          "tokenFee": "0xef68e7c694f40c8202821edf525de3782458639f",
          "amountS": "0xa",
          "amountB": "0x1",
          "amountFee": "0x2",
          "time": "0x5c4add07",
          "txHash": "0x9ab523ac966a375f02c5b22e275a6e4c9c621f83881650587bc331e95ee5e73"
        },
        ...
      ]
    }
  }
  ```

- ##### getOrderBook

  获取order book

  参数：

  ```json
  {
    "jsonrpc": "2.0",
    "method": "loopring_getOrderBook",
    "params": [
      {
        "level": 0,
        "size": 100,
        "market": "LRC-WETH"
      }
    ],
    "id": 1
  }
  ```

  返回：

  ```json
  {
    "id": 1,
    "jsonrpc": "2.0",
    "result": {
      "lastPrice": 0.0007,
      "sells": [
        {
          "amount": 1000,
          "price": 0.0007,
          "total": 0.7
        },
        ...
      ],
      "buys": [
        {
          "amount": 1000,
          "price": 0.00069,
          "total": 0.69
        },
        ...
      ]
    }
  }
  ```

- ##### getTikcer

  获取指定交易对的Ticker信息

  参数：

  ```json
  {"jsonrpc":"2.0","method":"loopring_getTikcer","params":["LRC-WETH"],"id":1}
  ```

  返回：

  ```
  {
    "id": 1,
    "jsonrpc": "2.0",
    "result": {
      "high": 30384.2,
      "low": 19283.2,
      "last": 28002.2,
      "vol": 1038,
      "amount": 1003839.32,
      "buy": 122321,
      "sell": 12388,
      "change": "50.12%"
    }
  }
  ```

#### 高级接口

- ##### getTransactionCount

  获取指定地址的transaction count

  参数：

  ```json
  {
    "jsonrpc": "2.0",
    "method": "loopring_getTransactionCount",
    "params": [
      "owner": "0xb94065482ad64d4c2b9252358d746b39e820a582",
      "latest"
    ],
    "id": 1
  }
  ```

  tag: latest 代表已经确认的transaction count ； pending代表 包含未确认的transaction count

  返回：

  ```json
  {
    "id":1,
    "jsonrpc": "2.0",
    "result": "0x10" // 16
  }
  ```

- ##### getGasPrice

  获取推荐的gasPrice

  参数: 

  ```json
  {"jsonrpc":"2.0","method":"loopring_getGasPrice","params":[],"id":1}
  ```

  返回：

  ```
  {
    "id": 1,
    "jsonrpc": "2.0",
    "result": "0x2540be400"
  }
  ```

- ##### getTransfers

  获取用户的转账记录

  参数：

  ```json
  {
    "jsonrpc": "2.0",
    "method": "loopring_getTransfers",
    "params": [
      {
        "tokens": [
          "LRC",
          "WETH"
        ],
        "type": "0x0",
        "status": "0x0",
        "pageNum": 1,
        "pageSize": 50
      }
    ],
    "id": 1
  }
  ```

  type: "0x0" 代表转入  "0x1"代表转出

  status："0x0" 代表成功，"0x1" 代表失败 ， "0x2"代表pending

  返回：

  ```
  {
    "id": 1,
    "jsonrpc": "2.0",
    "result": {
      "pageNum": 1,
      "pageSize": 50,
      "total": 60"records": [
        {
          "from": "0xb94065482ad64d4c2b9252358d746b39e820a582",
          "to": "0x6cc5f688a315f3dc28a7781717a9a798a59fda7b",
          "token": "LRC""amount": "1000.0000",
          "txHash": "0x9ab523ac966a375f02c5b22e275a6e4c9c621f83881650587bc331e895ee5e73",
          "time": "0x5c4add07",
          "status": 0
        },
        ...
      ]
    }
  }
  ```

- ##### getTransactions

  获取用户所有的Transaction

  参数：

  ```json
  {
    "jsonrpc": "2.0",
    "method": "loopring_getTransactions",
    "params": [
      {
        "owner": "0xb94065482ad64d4c2b9252358d746b39e820a582",
        "status": "0x0",
        "pageNum": 1,
        "pageSize": 50
      }
    ],
    "id": 1
  }
  ```

  status: "0x0" 代表 成功的 "0x1" 代表失败   "0x2" 代表pending

  返回：

  ```json
  {
    "id": 1,
    "jsonrpc": "2.0",
    "result": {
      "pageNum": 1,
      "pageSize": 50,
      "total": 60,
      "records": [
        {
          "from": "0xb94065482ad64d4c2b9252358d746b39e820a582",
          "to": "0x6cc5f688a315f3dc28a7781717a9a798a59fda7b",
          "value": "1000.0000",
          "gasPrice": "0x2540be400",
          "gasLimit": "0x5208",
          "gasUsed": "0x5208",
          "data": "0x",
          "nonce": "0x9",
          "hash": "0x9ab523ac966a375f02c5b22e275a6e4c9c621f83881650587bc331e95ee5e73",
          "blockNum": "0x6cc501",
          "time": "0x5c4add07",
          "status": 0
        },
        ...
      ]
    }
  }
  ```

- ##### 以太坊的全部接口

  详情见 [Ethereum JSON RPC API](https://github.com/ethereum/wiki/wiki/JSON-RPC)

### Socket接口

#### 普通接口

- ##### assets

  订阅用户的余额和授权

  参数：

  ```json
  {"owner":"0xb94065482ad64d4c2b9252358d746b39e820a582","tokens":["LRC","WETH"]}
  ```

  返回：

  ```js
  [
    {
      "symbol": "LRC",
      "balance": "0x1326beb03e0a0000",
      "allowance": "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    },
    ...
  ]
  ```

- ##### orders

  订阅用户的订单

  参数：

  ```json
  {
    "owner": "0xb94065482ad64d4c2b9252358d746b39e820a582",
    "market": "LRC-WETH",
    "status": "0x0"
  }
  ```

  返回：

  ```json
  [
    {
      "owner": "0xb94065482ad64d4c2b9252358d746b39e820a582",
      "version": "0x0",
      "tokenS": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
      "tokenB": "0xef68e7c694f40c8202821edf525de3782458639f",
      "amountS": "0xde0b6b3a7640000",
      "validSince": "0x5c4b0cb3",
      "amountB": "0x3635c9adc5dea00000",
      "params": {
        "validUnit": "0x5c4cacb3",
        "allOrNone": "0x0",
        "sig": "0xa3f20717a250c2b0b729b7e5becbff67fdaef7e0699da4de7ca5895b02a170a12d887fd3b17bfdce3481f10bea41f45ba9f709d39ce8325427b57afcfc994cee1b",
        "dualAuthAddr": "0x7ebdf3751f63a5fc1742ba98ee34392ce82fa8dd",
        "dualAuthPrivateKey": "0xc3d695ee4fcb7f14b8cf08a1d588736264ff0d34d6b9b0893a820fe01d1086a6"
      },
      "feeParams": {
        "tokenFee": "0xef68e7c694f40c8202821edf525de3782458639f",
        "amountFee": "0xde0b6b3a7640000"
      },
      "signType": "0x0"
    },
    ...
  ]
  ```

- ##### trades

  定于用户的交易记录

  参数 ：

  ```json
  {"owner":"0xb94065482ad64d4c2b9252358d746b39e820a582","market":"LRC-WETH"}
  ```

  返回：

  ```json
  [
    {
      "orderHash": "",
      "ringHash": "",
      "tokenS": "0xef68e7c694f40c8202821edf525de3782458639f",
      "tokenB": "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
      "tokenFee": "0xef68e7c694f40c8202821edf525de3782458639f",
      "amountS": "0xa",
      "amountB": "0x1",
      "amountFee": "0x2",
      "time": "0x5c4add07",
      "txHash": "0x9ab523ac966a375f02c5b22e275a6e4c9c621f83881650587bc331e95ee5e73"
    },
    ...
  ]
  ```

- ##### orderbook

  订阅orderbook

参数：

```json
 {"level":0,"size":100,"market":"LRC-WETH"}
```

返回:  

```json
{
  "lastPrice": 0.0007,
  "sells": [
    {
      "amount": 1000,
      "price": 0.0007,
      "total": 0.7
    },
    ...
  ],
  "buys": [
    {
      "amount": 1000,
      "price": 0.00069,
      "total": 0.69
    },
    ...
  ]
}
```

- ##### ticker

  订阅Ticker数据

  参数：

  ```json
  {"market":"LRC-WETH"}
  ```

  返回：

  ```json
  {
      "high" : 30384.2,
      "low" : 19283.2,
      "last" : 28002.2,
      "vol" : 1038,
      "amount" : 1003839.32,
      "buy" : 122321,
      "sell" : 12388,
      "change" : "50.12%"
  }
  ```

  #### 高级接口

- ##### transfers

  订阅用户的transfer事件信息

  参数:

  ```json
   {"tokens":["LRC","WETH"],"type":"0x0","status":"0x0"}
  ```

  type: "0x0" 代表转入  "0x1"代表转出

  status："0x0" 代表成功，"0x1" 代表失败 ， "0x2"代表pending

  返回：

  ```json
  [
    {
      "from": "0xb94065482ad64d4c2b9252358d746b39e820a582",
      "to": "0x6cc5f688a315f3dc28a7781717a9a798a59fda7b",
      "token": "LRC",
      "amount": "1000.0000",
      "txHash": "0x9ab523ac966a375f02c5b22e275a6e4c9c621f83881650587bc331e895ee5e73",
      "time": "0x5c4add07",
      "status": 0
    },
    ...
  ]
  ```

- ##### transactions

  订阅用户的transaction数据

  参数：

  ```json
  {"owner":"0xb94065482ad64d4c2b9252358d746b39e820a582"}
  ```

  返回：

  ```json
  [
    {
      "from": "0xb94065482ad64d4c2b9252358d746b39e820a582",
      "to": "0x6cc5f688a315f3dc28a7781717a9a798a59fda7b",
      "value": "1000.0000",
      "gasPrice": "0x2540be400",
      "gasLimit": "0x5208",
      "gasUsed": "0x5208",
      "data": "0x",
      "nonce": "0x9",
      "hash": "0x9ab523ac966a375f02c5b22e275a6e4c9c621f83881650587bc331e95ee5e73",
      "blockNum": "0x6cc501",
      "time": "0x5c4add07",
      "status": 0
    },
    ...
  ]
  ```

