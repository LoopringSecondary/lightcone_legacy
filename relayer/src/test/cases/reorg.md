## 分叉事件测试用例

### 测试分叉事件中的Fill

1. 测试分叉事件中的Fill

   - 目标：测试分叉事件影响的Fill是不是被正确删除

   - 测试前置条件：

     1. 设置一条Fill，block 设置为100。db存入该条Fill
     2. 设置一条block event。设置block为99
     3. 设置一条相同order Hash的Fill，block 为 101

   - 测试步骤和结果校验：

     1. 发出 block event 

        ==> 验证 fill是不是被删除

     2. 发出第二条fill

        ==> 验证数据库是不是正确存入该fill，socket是不是正确推送；

   - 状态: Planned

   - 拥有者: 亚东, 于红雨

   - 其他信息：NA

### 测试分叉事件中的Order

1. 测试分叉事件影响的order

   - 目标：测试分叉事件影响的order是不是被正确重新提交，order book有没有重新变化
   - 测试前置条件：

     1. 设置卖出 100 LRC 的order，提交该订单
     2. 设置 该订单在block 为100 时成交50 LRC
     3. 设置一条block event。设置block为99
   - 测试步骤和结果校验：

     1. 发出block event
        ==> 验证order的成交量减少50LRC，可成交量是不是增加50LRC；Order book对应价格的amount是不是增加50LRC。
   - 状态: Planned
   - 拥有者: 亚东
   - 其他信息：NA

### 测试分叉事件中的Activity

1. 测试分叉事件中的Activity，在新链上仍然有

   - 目标：测试分叉事件影响的Activity是不是被改成pending状态，后续同样的事件再来发送过来，是不是正确更新

   - 测试前置条件：

     1. dispatch一条transfer eth的activity，block设置为100
     2. 设置一条block event ，block设置为99
     3. 设置一条transfer eth的success 的activity，block 为101

   - 测试步骤和结果校验：

     1. 发出block event 

        ==> 验证 activity 的状态是不是改为 pending

     2. 发出第二条activity

        ==> 验证 activity是不是被更新为success的activity

   - 状态: Planned

   - 拥有者: 亚东、红雨

   - 其他信息：NA

2. 测试分叉事件中的Activity，在新链上有相同nonce其他事件

   - 目标：测试分叉事件影响的Activity是不是被改成pending状态，后续相同nonce其他事件发出，activity是不是正确处理

   - 测试前置条件：

     1. 存入db一条transfer的activity，nonce为10，txHash = "0x23c90be4c23abe114cbcc5b8cf7a0418ca35a70c0859ffdcfc9c4d26d422a03f"，block设置为100
     2. 设置一条block event ，block设置为99
     3. 设置一条transfer的success 的activity，nonce为10， txHash = "0x33c90be4c23abe114cbcc5b8cf7a0418ca35a70c0859ffdcfc9c4d26d422a03f", block 为101

   - 测试步骤和结果校验：

     1. 发出block event 

        ==> 验证 activity 的状态是不是改为 pending

     2. 发出第二条activity

        ==> 验证 第一条activity被删除，第二条是不是为SUCCESS。

   - 状态: Planned

   - 拥有者: 亚东、红雨

   - 其他信息：NA

## 测试分叉事件中的账户余额和授权

1. 测试分叉事件中账户余额和授权

   - 目标：测试分叉事件影响的账户余额和授权是不是更新为分叉后的最新值

   - 测试前置条件:

     1. 设置A在高度100 转入 50ETH，最新余额为100 ETH
     2. 设置A在高度101 授权 LRC 1000
     3. 设置block event ，block 设置为99

   - 测试步骤及结果验证

     1. 发出block event

        ==> 验证 A 的ETH余额是不是 50ETH，授权是不是0 LRC

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：NA

### 测试分叉事件中token burn rate

1. 测试分叉事件中 token burn rate有没有正确取最新的值

   - 目标：测试分叉事件 受影响token burn rate 有没有重新取最新值

   - 测试前置条件：

     1. 设置 GTO的burn rate 为0.5
     2. 设置 GTO的burn rate 在 block 100 更新为0.25
     3. 设置block event，block 为99

   - 测试步骤及结果验证：

     1. 发出 block event

        ==> 验证 GTO的burn rate是否为0.5

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：NA

### 测试分叉事件中K线数据

1. 测试分叉事件中的K线数据

   - 目标：测试分叉事件影响的K线数据是否被正确删除

   - 测试前置条件：

     1. 设置block = 100,  ohlcdata 100 LRC -> 0.1WETH  
     2. 设置block = 101,  ohlcdata 150 LRC -> 0.2WETH  
     3. 设置block = 102,  ohlcdata 150 LRC -> 0.2WETH 
     4. 设置block event ，block =100

   - 测试步骤和结果校验：

     1. 发送 这三条ohlcdata 

     2. 发送 block event

        ==> 验证 LRC-WETH市场的K线数据只有 100 LRC

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：NA


​     
