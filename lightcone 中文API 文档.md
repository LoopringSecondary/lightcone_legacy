## lightcone relay 中文API 文档

### 主要接口

#### 基础接口

- ##### getServerTime

  获取server 的时间戳，单位是毫秒。

  参数：

  {"jsonrpc":"2.0","method":"loopring_getServerTime","params":[],"id":1}

  返回：

  ```js
  {
    "id":1,
    "jsonrpc": "2.0",
    "result": 1548410119809
  }
  ```

- ###### getTokenMetaData 

  获取Relay支持交易的token的信息，包括最小下单量，最大下单量，保留小数位，地址, symbol,decimal等

  参数：{"jsonrpc":"2.0","method":"loopring_getTokenMetaData","params":["LRC","WETH"],"id":1}

  若不指定任何token，则返回全部支持的token信息

  返回：

  ```js
  {
    "id":1,
    "jsonrpc": "2.0",
     "result": [{
          minTradeAmount:0,
         	maxTradeAmount:100000000,
         	precision:5,
         	decimal:18,
         	symbol:"LRC",
         	address:"0xef68e7c694f40c8202821edf525de3782458639f"
     },{
          minTradeAmount:0,
         	maxTradeAmount:100000000,
         	precision:5,
         	decimal:18,
         	symbol:"WETH",
         	address:"0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2"
     }]
  }
  ```

- ##### getMarketMetaData

  获取Relay支持的市场信息。包括市场symbol, precisionForAmount, precisionForTotal,precisionForPrice

  参数： {"jsonrpc":"2.0","method":"loopring_getMarketMetaData","params":["LRC-WETH"],"id":1}

  若不指定任何market，则返回全部支持的market信息。

  返回：

  ```js
  {
    "id":1,
    "jsonrpc": "2.0",
     "result": [{
          symbol:"LRC-WETH",
         	precisionForAmount:5,
         	precisionForTotal:8,
         	precisionForPrice:5
     }]
  }
  ```

- ##### getAssets

  获取指定用户的指定token的余额和授权信息

  参数：{"jsonrpc":"2.0","method":"loopring_getAssets","params":[{"owner":"0xb94065482ad64d4c2b9252358d746b39e820a582",tokens:["LRC"]}],"id":1}

  若token不指定则返回relay支持的全部token的余额和授权

  返回：

  ```
  {
    "id":1,
    "jsonrpc": "2.0",
     "result": {"owner":"0xb94065482ad64d4c2b9252358d746b39e820a582",tokens:[{
         symbol:"LRC",
         balance:"0x1326beb03e0a0000",
         allowance:"0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
     }]
  }
  ```

- ##### submitOrder

  用户提交订单

  Order结构：

  - owner      	 Address
  - version            Int
  - tokenS            Address
  - tokenB           Address
  - AmountS       Hex String 
  - AmountB       Hex String
  -  validSince     Timestamp (seconds)
  - validUntil       Timestamp (seconds)

- 