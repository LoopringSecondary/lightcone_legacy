## 恢复逻辑
---
测试不同模块的恢复情况

 - [AccountManager恢复](#account-manager)
 - [MarketManager恢复](#market-manager)
 - [OrderbookManager恢复](#orderbook-manager)
 
---

### <a name="account-manager"></a>AccountManager的恢复
---
分为：系统重启时的恢复，单独恢复并且过期以及取消的订单时，

---

1. 系统重启时的恢复
    - **Objective**：测试当数据库中存在订单时，重启需要恢复AccountManager、MarketManger和OrderbookManager
    - **测试设置**：
        1. 设置2个新账户，余额都设置为1000LRC，授权为1000LRC
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
        2. 触发AccountManagerActor、MarkerManagerActor和OrderbookManagerActor的重启
        4. 等待回复完成 => 
            - **验证**：
                1. **读取市场深度**：不为空，并且恢复到重启之前的情况
                1. **读取我的成交**：为空
                1. **读取市场成交**：为空
                1. **读取我的账号**：恢复到重启之前的情况
        
    - **状态**: Planned
    - **拥有者**: 红雨
 
   ---   
1. 自身单独恢复
    - **Objective**：AccountManagerActor单独重启时的逻辑，其中如果有订单过期和取消时，是否重启过滤掉了，并且MarketManagerActor和OrderbookManager中对应的数据是否删除掉了
    - **测试设置**：
        1. 设置2个新账户，余额都设置为1000LRC，授权为1000LRC
        2. account1下三个卖单单分别为：sell:100LRC,buy:1WETH,sell:80LRC,buy:1WETH,sell:60LRC,buy:1WETH，下两个买单：sell:1WETH,buy:150LRC,sell:1WETH,buy:155LRC
            account2下两卖单单分别为：sell:110LRC,buy:1WETH,sell:90LRC,buy:1WETH， 下三个买单：sell:1WETH,buy:145LRC,sell:1WETH,buy:130LRC
            但是其中account1订单的有效期为当前时间+10s
        2. 依次上述提交订单 => 
            - **验证**：
                1. **返回结果**：提交成功
                1. **读取我的订单**：getOrders都不为空，提交的所有订单都存在，并且状态都为Pending
                1. **读取市场深度**：不为空，加起来之和(测试撮合，不区分粒度等情况)，需要为卖单：440LRC，买单为：580LRC
                1. **读取我的成交**：为空
                1. **读取市场成交**：为空
                1. **读取我的账号**：余额等不变，但是可用金额需要减掉订单占用部分
        2. 取消account2的第一个订单，
        3. 停止AccountManagerActor
        4. sleep(10s)等待订单过期，等待订单过期
        2. 重启AccountManagerActor
        4. 等待恢复完成 => 
            - **验证**：
                1. **读取市场深度**：不为空，加起来之和(测试撮合，不区分粒度等情况)，需要为卖单：230LRC，买单为：580LRC
                1. **读取我的成交**：为空
                1. **读取市场成交**：为空
                1. **读取我的账号**：恢复到重启之前的情况
        
    - **状态**: Planned
    - **拥有者**: 红雨

### <a name="market-manager"></a> MarketManager恢复
---
分为：系统重启时恢复(合并到AccountManager的测试中中)，单独自身恢复

---

1. 单独自身恢复
    - **Objective**：测试当某一个市场重启时，是否能成功恢复，其中会涉及一些操作如订单被取消、订单被fill等事件
    - **测试设置和验证**：
        1. 设置2个新账户，余额都设置为1000LRC，授权为1000LRC
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
		3. 停止MarketManagerActor
		4. sleep(10s)，等待其中一个时间过期
		5. 取消其中的另一个订单
		3. 发送Fill事件，更改某个订单的filledAmount        
		2. 重启LRC-WETH市场Actor
		4. 等待回复完成 => 
        	- **验证**：
		        1. **读取市场深度**：不为空，并且需要减掉取消和fill了的订单金额
		        1. **读取我的成交**：为空
		        1. **读取市场成交**：为空
		        1. **读取我的账号**：余额等不变，但是可用金额需要减掉订单占用部分
    - **状态**: Planned
    - **拥有者**: 红雨
    - **其他信息**：NA


### <a name="order-manager"></a> OrderbookManager恢复
---
分为：系统重启恢复(合并到AccountManager的测试中中)，单独自身恢复

---

1. 单独自身恢复
    - **Objective**：测试当某一个市场重启时，是否能成功恢复
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
		2. 然后通过发送消息或其他方式触发LRC-WETH市场的OrderbookManagerManagerActor的重启
		4. 等待回复完成 => 
        	- **验证**：
		        1. **读取市场深度**：不为空，并且恢复到重启之前的情况
		        1. **读取我的成交**：为空
		        1. **读取市场成交**：为空
		        1. **读取我的账号**：恢复到重启之前的情况
    - **状态**: Planned
    - **拥有者**: 红雨
    - **其他信息**：NA

    
