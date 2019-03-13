## 撮合逻辑
---
订单完全成交后是否已经更新了数据库
测试撮合逻辑，不同的撮合逻辑下对订单以及orderbook的影响，分为：

 - [两个完全匹配的订单](#full-match)
 - 

---

### <a name="full-match"></a>两个匹配的订单

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
        1. **读取我的账号**: 账号1的余额分别为：LRC:`balance=890`,WETH:`balance=1`
        账号2的余额分别为：LRC：`balance=100`, WETH: `balance=8.9`
        
    - **状态**: Planned
    - **拥有者**: 红雨

### <a name=""></a> 
        
    
