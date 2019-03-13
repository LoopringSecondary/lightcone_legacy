## 转账事件测试用例

转账事件流程测试主要分为[ETH转账](#eth-transfer)，[WETH的Warp](#weth-wrap), [WETH的Unwrap](#weth-unwrap), [ERC20 Token Transfer](#erc20-transfer), [转账对订单的影响](#transfer-order)

###  <a name="eth-transfer"></a> ETH转账

1. 测试成功的ETH转账事件流程

   - 目标：测试成功的ETH转账过程中，Activity 的解析与推送，账户余额的更新，发送地址Nonce的更新。

   - 测试前置条件：

     1. A地址余额为 20 ETH；B地址余额不限制；
     2. 正确设置一个A转账 10 ETH到B的Ethereum transaction

   - 测试步骤及结果验证：

     1. 发送转账 transaction

     2. 发出 A 转出10 ETH的Activity，B转入10ETH的Activity, A 的最新pending nonce 

        ==> 验证 db 正确存入这两条Activity， socket正确推送这两条Activity，A的pending nonce更新

     3. 发出A转出10ETH的成功的Activity，B转入10ETH成功的Acitivity, A 地址AddressBalanceUpdatedEvent 和 B地址 AddressBalanceUpdatedEvent

        ==>  验证db正确更新Activity的状态更新为Success ;socket 正确推送这两条成功的转账Activity；A的ETH余额为10 ETH - 油费，B的余额 + 10 ETH；

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：NA

2. 测试失败的ETH转账事件流程

   - 目标：测试失败的ETH转账过程中，Activity 的解析与推送，账户余额不变，发送地址Nonce的更新。

   - 测试前置条件：

     1. A地址余额为 20 ETH；B地址余额不限制；
     2. 设置一个A转账 10 ETH到B的Ethereum transaction，transaction的gas limit 设置为20000.

   - 测试步骤和结果验证：

     1. 发送转账transaction

     2. 发出 A 转出10 ETH的Activity，B转入10ETH的Activity, A 的最新pending nonce 

        ==> 验证 db 正确存入这两条Activity， socket正确推送这两条Activity，A的pending nonce更新

     3. 发出A转出10ETH的失败的Activity，B转入10ETH失败的Acitivity, A地址的AddressBalanceUpdatedEvent

        ==> db把 Pending的Activity 状态改成Failed；socket推送这两条失败的Activity；A的ETH余额减少油费，B的余额不变；

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：NA

### <a name="weth-wrap"></a> WETH Wrap 

1. 测试WETH Wrap成功的事件流程

   - 目标：测试在成功的WETH Wrap过程中Acitivity的解析和推送，WETH余额和ETH余额的更新变化。

   - 测试前置条件：

     1. 设置A地址 ETH余额为20ETH
     2. 正确设置A地址Wrap 10 ETH的Ethereum transaction

   - 测试步骤及结果验证：

     1. 发送Wrap transaction

     2. 发出 A地址 token 为ETH的pending wrap activity 和token为WETH的pending wrap activity

        ==> 验证 db存入了上面两条pending 的activity；socket  正确推送过去这两条activity；

     3. 发出A地址 token为ETH成功的wrap activity和token为WETH成功的wrap activity，A地址ETH变化和WETH的AddressBalanceUpdatedEvent

        ==>  db把pending的activity更新为成功状态；socket 推送这两条成功的activity；A  的ETH 余额 - （10+油费），WETH 余额 + 10

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：

     1. 在ETH转账中已经测试了账户nonce，因此这里不再重复测试。
     2. socket的推送是根据订阅条件来推送的，可能两条activity不会同时都推送

2. 测试WETH Wrap失败的事件流程

   - 目标：测试在失败的WETH Wrap过程中Acitivity的解析和推送，WETH余额和ETH余额的更新情况。

   - 测试前置条件：

     1. 设置A地址 ETH余额为20ETH
     2. 设置A地址Wrap 10 ETH的Ethereum transaction，transaction的gas limit设置为20000

   - 测试步骤及结果验证：

     1. 发送wrap transaction

     2. 发出 A地址 token 为ETH的pending wrap activity 和token为WETH的pending wrap activity

        ==> 验证 db存入了上面两条pending 的activity；socket  正确推送过去这两条activity；

     3. 发出A地址 token为ETH失败的wrap activity和token为WETH失败的wrap activity，A地址ETH变化的AddressBalanceUpdatedEvent

        ==>  db把pending的activity更新为失败状态；socket 推送这两条失败的activity；A 的ETH 余额 - 油费，WETH 余额 不变

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：

     1. 在ETH转账中已经测试了账户nonce，因此这里不再重复测试。
     2. socket的推送是根据订阅条件来推送的，可能两条activity不会同时都推送

### <a name="weth-unwrap"></a> WETH Unwrap

1. 测试WETH Unwrap成功的事件流程

   - 目标：测试在成功的WETH Unwrap过程中Acitivity的解析和推送，WETH余额和ETH余额的更新变化。

   - 测试前置条件：

     1. 设置A的ETH余额为10，WETH余额为20
     2. 正确设置A unwrap 10 个WETH的Ethereum Transaction

   - 测试步骤及结果验证：

     1. 发送unwarp transaction

     2. 发出A地址token为ETH的pending unwarp activity 和token 为weth 的pending unwrap activity

        ==> db 存入上面两条pending 的activity；socket 正确推送这两条activity

     3. 发出A地址token为ETH的成功的 unwarp activity 和token 为weth 的成功的 unwrap activity；A地址 Eth 和Weth变化的AddressBalanceUpdatedEvent

        ==> db将两条pending的activity改为success状态；A的ETH增加 10-油费；Weth减少10

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：

     1. socket的推送是根据订阅条件来推送的，需要收到两条activity需要启动两个客户端分别订阅。

2. 测试weth unwrap 失败的事件流程

   - 目标：测试在失败的WETH Unwrap过程中Acitivity的解析和推送，WETH余额和ETH余额的更新变化。

   - 测试前置条件：

     1. 设置A的ETH余额为10，WETH余额为20
     2. 设置A unwrap 30 个WETH的Ethereum Transaction

   - 测试步骤及结果验证：

     1. 发送unwarp transaction

     2. 发出A地址token为ETH的pending unwarp activity 和token 为weth 的pending unwrap activity

        ==> db 存入上面两条pending 的activity；socket 正确推送这两条activity

     3. 发出A地址token为ETH的失败的 unwarp activity 和token 为weth 的失败的 unwrap activity；A地址 Eth 变化的AddressBalanceUpdatedEvent

        ==> db将两条pending的activity改为failed状态；A的ETH减少 油费；Weth不变

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：

     1. socket的推送是根据订阅条件来推送的，需要收到两条activity需要启动两个客户端分别订阅。

###  <a name="erc20-transfer"></a>ERC20 token 转账

1. 测试成功的token转账流程

   - 目标：测试成功的token转账流程中，activity的解析，存储和推送，以及发送者和接收者余额的变化。

     ​	   该测试中不涉及token的余额变化引起的order可成交量和状态的变化以及orderbook的变化；

   - 测试前置条件：

     1. 设置A的ETH余额为10,LRC 余额为1000
     2. 正确设置A转100LRC到B的Ethereum transaction

   - 测试步骤及结果验证：

     1. 发送token transfer transaction

     2. 发出 A 转出LRC的pending activity，B转入LRC的pending activity

        ==> db 存入上面两条pending 的activity；socket 正确推送这两条activity

     3. 发出 A 转出LRC的成功的 activity，B转入LRC的成功的 activity；发出A、B的AddressBalanceUpdatedEvent

        ==> db 把pending的activity更新为成功的activity；socket 正确推送activity到A和B；A的LRC 余额减少 100，B的LRC余额减少 100；

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：NA

2. 测试失败的转账流程

   - 目标：测试失败的token转账流程中，activity的解析，存储和推送，发送者和接收者余额的变化情况。

   - 测试前置条件：

     1. 设置A的ETH余额为10,LRC 余额为50
     2. 设置A转100LRC到B的Ethereum transaction

   - 测试步骤和结果验证：

     1. 发送token transfer transaction

     2. 发出 A 转出LRC的pending activity，B转入LRC的pending activity

        ==> db 存入上面两条pending 的activity；socket 正确推送这两条activity

     3. 发出 A 转出LRC的失败的 activity，B转入LRC的失败的activity

        ==> db 把pending的activity更新为失败的activity；socket 正确推送activity 到A和B；A 和B的LRC余额不变；

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：NA

###  <a name="transfer-order"></a>测试成功的token转账引起订单和orderbook的变化

1.    测试token转出引起order可成交量影响的流程

   - 目标：测试token转账流程中，引起余额的变化，进而引起订单可成交量的变化和orderbook的变化

   - 前置条件：

     1. A的LRC 余额为200
     2. A的有一个卖出200 LRC的订单
     3. 设置A转出 100LRC到B的Ethereum transaction

   - 测试步骤和结果验证：

     1. 发送transfer Ethereum transaction

     2. 发出 A 转出LRC的pending activity，B转入LRC的pending activity

     3. 发出 A 转出LRC的成功的 activity，B转入LRC的成功的 activity；发出A、B的AddressBalanceUpdatedEvent

        ==> A 的订单可卖出量减少 100LRC；order book 的sells中对应的价格中量减少100LRC；socket推送A的订单变化和orderbook的变化

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：

     1. 对于Activity的推送和余额更新的推送已经在上面的测试中覆盖，这里不再重复测试。

2. 测试token转账引起order被取消的流程

   - 目标：测试token转账，因为order 的可成交量降为零而被取消，order book 跟着变化

   - 测试前置条件：

     1. A的LRC 余额为100
     2. A的有一个卖出100 LRC的订单
     3. 设置A转出 100LRC到B的Ethereum transaction

   - 测试步骤和结果验证：

     1. 发送transfer Ethereum transaction

     2. 发出 A 转出LRC的pending activity，B转入LRC的pending activity

     3. 发出 A 转出LRC的成功的 activity，B转入LRC的成功的 activity；发出A、B的AddressBalanceUpdatedEvent

        ==> A 的订单被取消；order book 的sells中对应的价格中量减少100LRC；socket推送A的订单变化和orderbook的变化;

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：

     1. 对于Activity的推送和余额更新的推送已经在上面的测试中覆盖，这里不再重复测试。

3. 测试token转账之后余额仍然充足的流程

   - 目标：测试token转账，余额仍然充足情况，订单和orderbook是不是受到影响。

   - 测试前置条件：

     1. A的LRC 余额为1000
     2. A的有一个卖出100 LRC的订单
     3. 设置A转出 100LRC到B的Ethereum transaction

   - 测试步骤及结果验证：

     1. 发送transfer Ethereum transaction

     2. 发出 A 转出LRC的pending activity，B转入LRC的pending activity

     3. 发出 A 转出LRC的成功的 activity，B转入LRC的成功的 activity；发出A、B的AddressBalanceUpdatedEvent

        ==> A 的订单大小和状态不变；order book 的大小不变；

   - 状态: Planned
   - 拥有者: 亚东
   - 其他信息：NA