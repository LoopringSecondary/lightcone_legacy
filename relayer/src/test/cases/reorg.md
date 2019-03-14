## 分叉事件测试用例

### 测试分叉事件中的Fill

1. 测试分叉事件中的Fill

   - 目标：测试分叉事件影响的Fill是不是被正确删除

   - 测试前置条件：

     1. 设置一条Fill，block 设置为100。db存入该条Fill
     2. 设置一条block event。设置block为99

   - 测试步骤和结果校验：

     1. 发出 block event 

        ==> 验证 fill是不是被删除；验证Order的成交量减少100LRC，可成交量是不是增加100LRC；Order book对应价格的amount是不是增加100LRC。

   - 状态: Planned

   - 拥有者: 亚东

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
        ==> 验证Order的成交量减少50LRC，可成交量是不是增加50LRC；Order book对应价格的amount是不是增加50LRC。
   - 状态: Planned
   - 拥有者: 亚东
   - 其他信息：NA

### 测试分叉事件中的Activity

1. 测试分叉事件中的Activity

   - 目标：测试分叉事件影响的Activity是不是被改成pending状态

   - 测试前置条件：

     1. 存入db一条transfer eth的activity，block设置为100
     2. 设置一条block event ，block设置为99

   - 测试步骤和结果校验：

     1. 发出block event 

        ==> 验证 acitivity 的状态是不是改为 pending

   - 状态: Planned

   - 拥有者: 亚东

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


​     