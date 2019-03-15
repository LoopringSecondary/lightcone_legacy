## 授权事件测试用例

授权事件测试包括[普通的授权事件](#common-approval)，[授权流程测试](#approval)和[授权对订单的影响](#approval-order)



### <a name="common-approval"></a>普通授权事件

1. 测试普通的授权事件

   - 目标：测试普通的授权事件中用户的对路印合约的授权不会变化

   - 测试前置条件：

     1. 设置一个A对B地址授权LRC 100 的Ethereum transaction

   - 测试步骤和结果验证：

     1. 发送approve transaction

     2. 发出 A pending的授权activity

        ==> 验证db正确存入该条activity; socket 正确推送该条activity

     3. 发出A success的授权acitivity

        ==> 验证 db更新pending activity为成功的activity；socket 推送成功的activity；A的授权值不变

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：NA

### <a name="approval"></a> 授权路印合约

1. 测试成功的授权事件

   - 目标:测试成功的授权流程中activity 的解析，存储和推送，授权值的更新。

   - 测试前置条件：

     1. 设置A的授权值为零
     2. 设置A授权路印合约10000LRC的ethereum transaction

   - 测试步骤和结果验证：

     1. 发送approve transaction

     2. 发出 A pending的授权activity

        ==> 验证db正确存入该条activity; socket 正确推送该条activity

     3. 发出A success的授权activity，A 的AddressAllowanceUpdatedEvent

        ==> 验证 db更新pending activity为成功的activity；socket 推送成功的activity，socket 推送A 授权变化的Account事件；A的授权变为 10000LRC

   - 状态: Planned
   - 拥有者: 亚东
   - 其他信息：
     1. 授权值从非零的值变为零的授权与该测试相似，不再重复测试。

2. 测试失败的授权事件

   - 目标：测试授权失败流程中acitivity的解析，存储和推送，授权值影响。

   - 测试前置条件：

     1. 设置A对路印合约的授权值为100LRC
     2. 设置A授权路印合约10000LRC的ethereum transaction

   - 测试步骤和结果验证：

     1. 发送approve transaction

     2. 发出 A pending的授权activity

        ==> 验证db正确存入该条activity; socket 正确推送该条activity

     3. 发出失败的授权activity

        ==> 验证 db更新pending activity为失败的activity；socket 推送失败的activity

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：NA

###  <a name="approval-order"></a>授权流程对订单的影响

1. 测试授权减少对卖出订单影响

   - 目标：测试授权减少流程中，订单可以成交量和order book的影响。

   - 测试前置条件：

     1. 设置A对路印合约的授权值为 1000LRC
     2. 设置A卖出 1000 LRC的order
     3. 设置A授权路印合约为0的ethereum transaction

   - 测试步骤和结果验证：

     1. 发送approve transaction

     2. 发出 A pending的授权activity

     3. 发出A success的授权activity，A 的AddressAllowanceUpdatedEvent

        ==> 订单的可成交量变为0；orderbook中对应价格的sells amount 减少1000 LRC

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：

     1. activity的解析，推送和授权变化已经在前面测试中覆盖，这里不再重复测试。

2. 测试作为fee授权减少对订单的影响

   - 目标：测试作为fee的token授权减少流程中，订单可以成交量和order book的影响。

   - 测试前置条件：

      1. 设置A对路印合约的授权值为 1000LRC，GTO余额和授权充足
      2. 设置A卖出 2000 GTO的order，fee 为100LRC
      3. 设置A授权路印合约为0的ethereum transaction

   - 测试步骤和结果验证：

      1. 发送approve transaction

      2. 发出 A pending的授权activity

      3. 发出A success的授权activity，A 的AddressAllowanceUpdatedEvent

         ==> 订单的可成交量变为0；orderbook中对应价格的sells amount 减少2000 GTO

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：

3. 测试授权值增加流程

   - 目标：测试授权值增加流程中订单的可成交量和order book的影响

   - 测试前置条件：
      1. 设置A对路印合约的授权值为0LRC
      2. 设置A卖出 1000 LRC的order
      3. 设置A授权路印合约为10000的ethereum transaction

   - 测试步骤和结果验证：

      1. 发送approve transaction

      2. 发出 A pending的授权activity

      3. 发出A success的授权activity，A 的AddressAllowanceUpdatedEvent

         ==> 订单的可成交量变为1000LRC；orderbook中对应价格的sells amount 增加1000 LRC

   -  状态: Planned

   - 拥有者: 亚东

   - 其他信息：NA

4. 测试作为fee的token授权值增加流程

   - 目标：测试作为token授权值增加流程中订单的可成交量和order book的影响

   - 测试前置条件：

     1. 设置A对路印合约的授权值为0LRC，GTO余额和授权充足
     2. 设置A卖出 2000 GTO的order，fee 为100LRC
     3. 设置A授权路印合约为10000的ethereum transaction

   - 测试步骤和结果验证：

     1. 发送approve transaction

     2. 发出 A pending的授权activity

     3. 发出A success的授权activity，A 的AddressAllowanceUpdatedEvent

        ==> 订单的可成交量变为2000GTO；orderbook中对应价格的sells amount 增加2000 GTO

   -  状态: Planned

   - 拥有者: 亚东

   - 其他信息：NA
