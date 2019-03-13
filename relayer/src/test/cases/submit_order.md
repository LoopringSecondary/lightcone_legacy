## 提交订单流程(不涉及撮合)

在以下4种不同情形下提交订单的表现，分别根据[账户余额](#different-balance) ，[Market状态](#market-status) ， [账户取消状态](#owner-cutoff) ， [参数](#system-config)

### <a name="different-balance"></a>不同账户余额情况
--- 
分为5种情况：无余额无授权、有余额但是无授权，有余额有授权但是授权不足，有充足的余额和授权，连续下单

---    
1. 无余额无授权时
    - **Objective**：无余额无授权时，提交订单，应该返回提交失败，并且数据库状态应该为 `STATUS_SOFT_CANCELLED_LOW_BALANCE`
    - **测试设置**：
        1. 设置一个新账号，无余额无授权，并确认
        2. 下一个订单
    - **结果验证**：
        1. **返回结果**：返回结果应该是无法提交订单 `SubmitOrder.Res.status == false`
        1. **读取我的订单**：该订单的状态应该为 `STATUS_SOFT_CANCELLED_LOW_BALANCE`
        1. **读取市场深度**：为空
        1. **读取我的成交**: 应该为空
        1. **读取市场成交**： 应该为空
        1. **读取我的账号**: 余额和授权为0
    - **状态**: Planned
    - **拥有者**: 红雨
    - **其他信息**：NA
    
    ---    
1. 有余额但是授权为0
    - **Objective**：有余额但是无授权时，提交订单，应该返回提交成功，但是orderbook中应该没有显示
    - **测试设置**：
        1. 订单余额设置为100LRC，但是授权为0
        2. 提交订单
    - **结果验证**：
        1. **返回结果**：提交成功
        1. **读取我的订单**：该订单状态为：
        1. **读取市场深度**：为空
        1. **读取我的成交**：为空
        1. **读取市场成交**：为空
        1. **读取我的账号**：`balance=100,allowance=0`
    - **状态**: Planned
    - **拥有者**: 红雨
    - **其他信息**：NA
    
	---     
1. 有余额但是授权不足
    - **Objective**：有余额但是授权不足时，提交订单，应该返回提交成功，但是orderbook中显示的为授权金额减去交易费
    - **测试设置**：
        1. 订单余额设置为100LRC，但是授权为30
        2. 提交订单，sell：50LRC, fee:10LRC
    - **结果验证**：
        1. **返回结果**：提交成功
        1. **读取我的订单**：该订单状态为：PENDING
        1. **读取市场深度**：为25
        1. **读取我的成交**：为空
        1. **读取市场成交**：为空
        1. **读取我的账号**：`balance=100,allowance=50,avaliableBalance=70`
    - **状态**: Planned
    - **拥有者**: 红雨
    - **其他信息**：NA
    
    ---
1. 有余额授权也足够
    - **Objective**：有余额授权也足够时，提交订单，应该返回提交成功，并且orderbook中显示该订单的金额
    - **测试设置**：
        1. 订单余额设置为100LRC，但是授权为100
        2. 提交订单，sell：40LRC， fee:10LRC
    - **结果验证**：
        1. **返回结果**：提交成功
        1. **读取我的订单**：该订单状态为：PENDING
        1. **读取市场深度**：为40
        1. **读取我的成交**：为空
        1. **读取市场成交**：为空
        1. **读取我的账号**：`balance=100,allowance=100,avaliableBalance=50,avaliableAllowance=50`
    - **状态**: Planned
    - **拥有者**: 红雨
    - **其他信息**：NA

    ---
1. 连续下单时
    - **Objective**：设置新账户，有余额授权时，连续提交订单，最后一个提交失败，其余应该提交成功，并且orderbook中显示对应的金额
    - **测试设置和验证**：
        1. 订单余额设置为1000LRC，但是授权为1000
        2. 提交第一个订单，sell:100LRC, fee:20LRC => 
        	- **验证**：
		        1. **返回结果**：提交成功
		        1. **读取我的订单**：getOrders返回只有该订单，并且状态为：PENDING
		        1. **读取市场深度**：为100
		        1. **读取我的成交**：为空
		        1. **读取市场成交**：为空
		        1. **读取我的账号**：`balance=1000,allowance=1000,avaliableBalance=880,avaliableAllowance=880`
		        
        2. 提交第二个订单，sell：500LRC， fee：20LRC => 
        	- **验证**：
		        1. **返回结果**：提交成功
		        1. **读取我的订单**：getOrders返回两条订单，并且状态为：PENDING
		        1. **读取市场深度**：为600
		        1. **读取我的成交**：为空
		        1. **读取市场成交**：为空
		        1. **读取我的账号**：`balance=1000,allowance=1000,avaliableBalance=360,avaliableAllowance=360`
		        
        2. 提交第三个订单，sell：500LRC， fee：20LRC => 
        	- **验证**：
		        1. **返回结果**：提交成功
		        1. **读取我的订单**：getOrders返回三条订单，并且状态为：PENDING
		        1. **读取市场深度**：为946.153846
		        1. **读取我的成交**：为空
		        1. **读取市场成交**：为空
		        1. **读取我的账号**：`balance=1000,allowance=1000,avaliableBalance=0,avaliableAllowance=0`

		        
        2. 提交第四个订单，sell：500LRC， fee：20LRC => 
        	- **验证**：
		        1. **返回结果**：提交失败，`SubmitOrder.Res.status == false`
		        1. **读取我的订单**：getOrders返回四条订单，除本条外状态为：PENDING，本条状态为`STATUS_SOFT_CANCELLED_LOW_BALANCE`
		        1. **读取市场深度**：为946.153846
		        1. **读取我的成交**：为空
		        1. **读取市场成交**：为空
		        1. **读取我的账号**：`balance=1000,allowance=1000,avaliableBalance=0,avaliableAllowance=0`

    - **状态**: Planned
    - **拥有者**: 红雨
    - **其他信息**：NA
       
### <a name="owner-cutoff"></a>账户不同状态的情况
--- 
分为三种情况：发起过取消订单的Ethereum交易，发起过OwerCutoff的交易，发起过OwnerCutoffPair的交易

--- 
1. 发起过取消订单的Ethereum交易
    - **Objective**：设置新账户有足够的余额授权，然后发起取消订单额Ethereum交易，再次提交该订单，应该无法提交成功
    - **测试设置**：
        1. 设置新账号，并且设置余额为100LRC，授权为100LRC
        2. 提交订单，sell：40LRC, 10LRC
    - **结果验证**：
        1. **返回结果**：提交失败，`SubmitOrder.Res.status == false`
        1. **读取我的订单**：getOrders返回了该订单，但是状态为CANCELLED
        1. **读取市场深度**：为空
        1. **读取我的成交**：为空
        1. **读取市场成交**：为空
        1. **读取我的账号**：`balance=100,allowance=100,avaliableBalance=100,avaliableAllowance=100`
    - **状态**: Planned
    - **拥有者**: 红雨
    - **其他信息**：NA
    
	---
1. 发起过OwnerCutoff的Ethereum交易
    - **Objective**：设置新账户有足够的余额授权，然后触发OwnerCutoff事件，提交一笔在cutoff之前的订单，应该提交失败，但是如果提交cutoff之后的订单的话，可以提交成功
    - **测试设置和验证**：
        1. 设置新账户，订单余额设置为1000LRC，但是授权为1000
        2. 触发OwnerCutoff事件
        2. 提交第一个订单，sell:100LRC, fee:20LRC，validSince <= cutoff => 
        	- **验证**：
		        1. **返回结果**：提交失败
		        1. **读取我的订单**：getOrders返回一条数据，并且状态为：CANCELLED_BY_USER
		        1. **读取市场深度**：为空
		        1. **读取我的成交**：为空
		        1. **读取市场成交**：为空
		        1. **读取我的账号**：`balance=1000,allowance=1000,avaliableBalance=1000,avaliableAllowance=1000`
		2. 提交第二个订单，sell:100LRC, fee:20LRC，validSince > cutoff => 
		 	- **验证**：
		        1. **返回结果**：提交成功
		        1. **读取我的订单**：getOrders返回两条数据，该条订单的状态为：PENDING
		        1. **读取市场深度**：为100
		        1. **读取我的成交**：为空
		        1. **读取市场成交**：为空
		        1. **读取我的账号**：`balance=1000,allowance=1000,avaliableBalance=880,avaliableAllowance=880`
		               
    - **状态**: Planned
    - **拥有者**: 红雨
    - **其他信息**：NA
    
	---
1. 发起过OwnerCutoffPair的Ethereum交易
    - **Objective**：设置新账户有足够的余额授权，然后触发OwnerCutoffPair事件，提交一笔该市场的并且在cutoff之前的订单，应该提交失败，但是如果提交cutoff之后的或者其他市场的订单的话，可以提交成功
    - **测试设置和验证**：
        1. 设置新账户，订单余额设置为1000LRC和1000GTO，授权为1000LRC和1000GTO
        2. 触发OwnerCutoffPair事件
        2. 提交第一个取消的市场订单订单，sell:100LRC, fee:20LRC，validSince <= cutoff => 
        	- **验证**：
		        1. **返回结果**：提交失败
		        1. **读取我的订单**：getOrders返回一条数据，并且状态为：CANCELLED_BY_USER
		        1. **读取市场深度**：为空
		        1. **读取我的成交**：为空
		        1. **读取市场成交**：为空
		        1. **读取我的账号**：LRC的余额为：`balance=1000,allowance=1000,avaliableBalance=1000,avaliableAllowance=1000`
		2. 提交第二个订单，sell:100LRC, fee:20LRC，validSince > cutoff => 
		 	- **验证**：
		        1. **返回结果**：提交成功
		        1. **读取我的订单**：getOrders返回两条数据，该条订单的状态为：PENDING
		        1. **读取市场深度**：为100
		        1. **读取我的成交**：为空
		        1. **读取市场成交**：为空
		        1. **读取我的账号**：LRC的余额为：`balance=1000,allowance=1000,avaliableBalance=880,avaliableAllowance=880`
		2. 提交第三个订单，sell:100GTO, fee:20LRC，validSince <= cutoff 但是为另一个市场单=> 
		 	- **验证**：
		        1. **返回结果**：提交成功
		        1. **读取我的订单**：getOrders返回三条数据，该条订单的状态为：PENDING
		        1. **读取市场深度**：为100
		        1. **读取我的成交**：为空
		        1. **读取市场成交**：为空
		        1. **读取我的账号**：GTO的余额为：`balance=1000,allowance=1000,avaliableBalance=900,avaliableAllowance=900`   
		        LRC的余额为： `balance=1000,allowance=1000,avaliableBalance=860,avaliableAllowance=860`           
    - **状态**: Planned
    - **拥有者**: 红雨
    - **其他信息**：NA
    
### <a name="market-status"></a>Market不同状态的情况
--- 
测试在不同市场状态时，提交订单请求的结果

--- 
1. 市场为不同状态时
    - **Objective**：设置新账户有足够的余额授权，然后设置三个市场的状态分别为：Activity、Readonly、Terminated时，分别提交三笔订单，分别提交成功、失败、失败
    - **测试设置和验证**：
        1. 设置新账户，订单余额设置为1000LRC和1000GTO，授权为1000LRC和1000GTO
        2. 设置三个市场LRC-WETH，GTO-LRC，GTO-WETH，并且状态分别为Activity、Readonly、Terminated
        2. 提交第一个市场订单，sell:100LRC,buy:1WETH, fee:20LRC => 
        	- **验证**：
		        1. **返回结果**：提交成功
		        1. **读取我的订单**：getOrders返回一条数据，并且状态为：PENDING
		        1. **读取市场深度**：为100LRC
		        1. **读取我的成交**：为空
		        1. **读取市场成交**：为空
		        1. **读取我的账号**：LRC的余额为：`balance=1000,allowance=1000,avaliableBalance=880,avaliableAllowance=880`
		2. 提交第二个市场订单，sell:100GTO,buy:100LRC, fee:20LRC => 
		 	- **验证**：
		        1. **返回结果**：提交失败
		        1. **读取我的订单**：getOrders为空
		        1. **读取市场深度**：为空
		        1. **读取我的成交**：为空
		        1. **读取市场成交**：为空
		        1. **读取我的账号**：LRC的余额为：`balance=1000,allowance=1000,avaliableBalance=880,avaliableAllowance=880`
		2. 提交第三个市场订单，sell:100GTO,buy:1WEH， fee:20LRC => 
		 	- **验证**：
		        1. **返回结果**：提交失败
		        1. **读取我的订单**：getOrders为空
		        1. **读取市场深度**：为空
		        1. **读取我的成交**：为空
		        1. **读取市场成交**：为空
		        1. **读取我的账号**：GTO的余额为：`balance=1000,allowance=1000,avaliableBalance=1000,avaliableAllowance=1000`
    - **状态**: Planned
    - **拥有者**: 红雨
    - **其他信息**：NA

### <a name="system-config"></a>不同参数设置的情况
--- 
目前一种情况：灰尘单设置的影响

--- 
1. 订单金额与灰尘单金额比较
    - **Objective**：设置新账户有足够的余额授权，当提交的订单的金额小于灰尘单的金额时，应该无法提交成功
     - **测试设置和验证**：
        1. 设置新账户，订单余额设置为1000LRC，授权为1000LRC
        2.设置市场的灰尘单金额为$10
        2. 提交第一个订单，sell:100LRC, buy:1WETH, fee:20LRC => 
        	- **验证**：
		        1. **返回结果**：提交失败
		        1. **读取我的订单**：getOrders空
		        1. **读取市场深度**：为100LRC
		        1. **读取我的成交**：为空
		        1. **读取市场成交**：为空
		        1. **读取我的账号**：LRC的余额为：`balance=1000,allowance=1000,avaliableBalance=1000,avaliableAllowance=1000`
		2. 提交第二个订单，sell:900LRC, buy:1WETH, fee:20LRC => 
        	- **验证**：
		        1. **返回结果**：提交成功
		        1. **读取我的订单**：getOrders为一条数据，并且该订单状态为Pending
		        1. **读取市场深度**：为900LRC
		        1. **读取我的成交**：为空
		        1. **读取市场成交**：为空
		        1. **读取我的账号**：LRC的余额为：`balance=1000,allowance=1000,avaliableBalance=80,avaliableAllowance=80`
    - **状态**: Planned
    - **拥有者**: 红雨
    - **其他信息**：NA