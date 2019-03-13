## 撮合逻辑
---
订单完全成交后是否已经更新了数据库
测试撮合逻辑，不同的撮合逻辑下对订单以及orderbook的影响，分为：

 - [两个完全匹配的订单](#full-match)
 - [多个订单及用户](#multi-orders)
 - [匹配顺序选择](#match-order-selection)
 
---

### <a name="full-match"></a>两个匹配的订单
---
分为： 两个完全匹配的订单、其中一个订单被完全匹配、延迟匹配等三种情况

---

1. 测试两个完成匹配的订单的情况
    - **Objective**：提交两个完全匹配的订单，然后检查完全成交之后，订单的状态以及orderbook的状态
    - **测试设置**：
        1. 设置两个账号，分别有1000LRC和10WETH余额和授权
        1. 通过两个账号分别提交两个订单，分别为：Order(sell:100LRC,buy:1WETH,fee:10LRC), Order(sell:1WETH,buy:100LRC,fee:0.1WETH) 
        2. 等待提交环路的交易
        3. 发出Filled和RingMined事件
    - **结果验证**：
        1. **读取我的订单**：通过getOrders应该看到这两个订单都是完全成交的
        2. **读取市场深度**：为空
        1. **读取我的成交**: 两个账户分别有一个成交
        1. **读取市场成交**：LRC-WETH市场有一个成交
        1. **读取我的账号**: 账号1的余额分别为：LRC:`balance=890`,WETH:`balance=1`
        账号2的余额分别为：LRC：`balance=100`, WETH: `balance=8.9`
        
    - **状态**: Planned
    - **拥有者**: 红雨
 
   ---   
1. 其中一个订单完全匹配情况
    - **Objective**：提交两个匹配的订单，但是只有其中一个能完全成交，然后检查成交之后，订单的状态以及orderbook的状态
    - **测试设置**：
        1. 设置两个账号，分别有1000LRC和10WETH余额和授权
        1. 通过两个账号分别提交两个订单，分别为：Order(sell:200LRC,buy:2WETH,fee:20LRC), Order(sell:1WETH,buy:100LRC,fee:0.1WETH) 
        2. 等待提交环路的交易
        3. 发出Filled和RingMined事件
    - **结果验证**：
        1. **读取我的订单**：通过getOrders应该看到其中一个订单状态为`STATUS_COMPLETELY_FILLED`, 另外一个是：`STATUS_PARTIALLY_FILLED`，并且成交金额为100LRC
        2. **读取市场深度**：卖单为100LRC，买单为空
        1. **读取我的成交**: 两个账户分别有一个成交
        1. **读取市场成交**：LRC-WETH市场有一个成交
        1. **读取我的账号**: 账号1的余额分别为：LRC:`balance=890`,WETH:`balance=1`
        账号2的余额分别为：LRC：`balance=100`, WETH: `balance=8.9`
        
    - **状态**: Planned
    - **拥有者**: 红雨
    
   ---   
1. 延迟匹配的情况
    - **Objective**：提交两个匹配的订单，但是因为收益等无法及时撮合提交，然后降低收益，撮合提交之后，订单以及orderbook的状态
    - **测试设置**：
        1. 设置两个账号，分别有1000LRC和10WETH余额和授权，设置收益为$1，gasprice设置较大，为1000000000000
        1. 通过两个账号分别提交两个订单，分别为：Order(sell:100LRC,buy:1WETH,fee:10LRC), Order(sell:1WETH,buy:100LRC,fee:0.1WETH) 
        2. 因为收益不到，环路无法成交，=> 验证orderbook以及订单的状态
        2. 降低gasprice，使收益足够，并触发rematch，
        3. 等待提交环路的交易
        3. 发出Filled和RingMined事件
    - **结果验证**：
        1. **读取我的订单**：通过getOrders应该看到两个订单状态为`STATUS_COMPLETELY_FILLED`
        2. **读取市场深度**：卖单为空，买单为空
        1. **读取我的成交**: 两个账户分别有一个成交
        1. **读取市场成交**：LRC-WETH市场有一个成交
        1. **读取我的账号**: 账号1的余额分别为：LRC:`balance=890`, WETH:`balance=1`
        账号2的余额分别为：LRC：`balance=100`, WETH: `balance=8.9`
        
    - **状态**: Planned
    - **拥有者**: 红雨

### <a name="multi-orders"></a> 多个订单及用户的情况
---
分为：存在orderbook的情况下吃单，

---

1. 存在orderbook的情况下吃单(这块的具体参数，未能设置清楚，需要测试时再确定)
    - **Objective**：多个用户下了多个无法匹配的订单，orderbook的买单和卖单应该能符合，然后依次下两边可以吃掉的订单，然后成交历史、orderbook等需要能依次变化
    - **测试设置和验证**：
        1. 设置4个新账户，余额都设置为1000LRC，授权为1000LRC
        2. account1下三个卖单单分别为：sell:100LRC,buy:1WETH,sell:80LRC,buy:1WETH,sell:60LRC,buy:1WETH，下两个买单：sell:1WETH,buy:150LRC,sell:1WETH,buy:155LRC
        	account2下两卖单单分别为：sell:110LRC,buy:1WETH,sell:90LRC,buy:1WETH， 下三个买单：sell:1WETH,buy:145LRC,sell:1WETH,buy:130LRC
        2. 依次上述提交订单 => 
        	- **验证**：
		        1. **返回结果**：提交成功
		        1. **读取我的订单**：getOrders都不为空，提交的所有订单都存在，并且状态都为Pending
		        1. **读取市场深度**：不为空，加起来之和(测试撮合，不区分粒度等情况)，需要为卖单：440LRC，买单为：580LRC
		        1. **读取我的成交**：为空
		        1. **读取市场成交**：为空
		        1. **读取我的账号**：余额等不变，但是可用金额需要减掉订单占用部分
		2. account3开始下单依次开始吃买单和卖点，下卖单：sell:150LRC, buy:1WETH
		3. 等待提交撮合环路
		4. 发出对应的撮合事件 => 
        	- **验证**：
		        1. **返回结果**：提交成功
		        1. **读取我的订单**：getOrders都不为空，但是卖单需要有一条为已成交
		        1. **读取市场深度**：不为空，加起来之和，需要为卖单：440LRC，买单为：430LRC
		        1. **读取我的成交**：account3与某一个account的不为空
		        1. **读取市场成交**：不为空，至少一条
		        1. **读取我的账号**：account3增加weth减小lrc，其他的某一个增加lrc减小weth
		5. 下买单，重复以上的验证
    - **状态**: Planned
    - **拥有者**: 红雨
    - **其他信息**：NA


### <a name="match-order-selection"></a> 匹配顺序选择(是否有必要测试)
---
匹配顺序

---

        
    
