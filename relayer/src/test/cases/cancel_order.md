


## 取消订单

按订单的几个状态：[STATUS_PENDING](#pending)，[STATUS_PARTIALLY_FILLED](#partially)， [STATUS_ONCHAIN_CANCELLED_BY_USER](#onchain_cancelled)取消订单。
有效状态的订单可以被取消为各种状态，onchain cancel后不能再被软取消，在pending时同时验证按orderHash，owner，owner-marketPair, cutoffEvent,
ordersCancelledOnChainEvent取消，其他状态只验证按orderHash取消

 ###  <a name="pending"></a> STATUS_PENDING状态订单的取消

 1. **测试按orderHash取消订单流程**
    - **Objective**：测试按orderHash可以正确取消订单，不存在的orderHash会返回错误码

    - **测试设置**：

        1. 设置账号有1000个LRC，授权充足
        1. 在LRC-WETH市场下一个卖10个LRC的单，价格是0.01WETH，返回orderHash：O1
        1. 发送CancelOrder请求，请求参数orderHash：O1，status：STATUS_SOFT_CANCELLED_BY_USER
        	- 结果验证：
          	    1. **返回结果**：ERR_NONE
          	    1. **读取我的订单**：通过getOrders应该看到该订单，其中的status应该为STATUS_SOFT_CANCELLED_BY_USER
          	    1. **读取市场深度**：卖单深度应该减掉10
          	    1. **读取我的成交**: 应该为空
          	    1. **读取市场成交**： 应该为空
          	    1. **读取我的账号**: LRC 可用余额应为1000

        1. 发送CancelOrder请求，请求参数O2，STATUS_SOFT_CANCELLED_BY_USER
        	- 结果验证：
        		1. **返回结果**：ERR_ORDER_NOT_EXIST

    - **状态**: Planned

    - **拥有者**: 杜永丰

    - **其他信息**：NA

1. **测试按owner取消订单流程**

    - **Objective**：测试按owner可以正确取消订单，owner不存在订单的会返回错误码

    - **测试设置**：

        1. 设置A1账号有1000个LRC，1000个GTO，授权充足
        1. 在LRC-WETH市场下一个卖10个LRC的单，价格是0.01WETH，返回orderHash：O1
        1. 在GTO-WETH市场下一个卖10个GTO的单，价格是0.001WETH，返回orderHash：O2
        1. 发送CancelOrder请求，请求参数owner：A1
            - 结果验证：
          	    1. **返回结果**：ERR_NONE
          	    1. **读取我的订单**：通过getOrders应该看到该订单，其中的status应该为STATUS_SOFT_CANCELLED_BY_USER
          	    1. **读取市场深度**：2个市场深度都为0
          	    1. **读取我的成交**: 应该为空
          	    1. **读取市场成交**： 应该为空
          	    1. **读取我的账号**: LRC 可用余额应为1000，GTO可用余额应为1000

        1. 发送CancelOrder请求，请求参数owner：A2，status：STATUS_SOFT_CANCELLED_BY_USER
        	- 结果验证：
        		1. **返回结果**：ERR_ORDER_NOT_EXIST

    - **状态**: Planned

    - **拥有者**: 杜永丰

    - **其他信息**：NA
1. **测试按owner-marketPair取消订单流程**
    - **Objective**：测试按owner和marketPair可以正确取消订单，不存在订单的会返回错误码

    - **测试设置**：

        1. 设置A1账号有1000个LRC，授权充足
        1. 在LRC-WETH市场下一个卖10个LRC的单，价格是0.01WETH，返回orderHash：O1
        1. 发送CancelOrder请求，请求参数owner：A1，marketPair：LRC-WETH
        	- 结果验证：
          	    1. **返回结果**：ERR_NONE
          	    1. **读取我的订单**：通过getOrders应该看到该订单，其中的status应该为STATUS_SOFT_CANCELLED_BY_USER
          	    1. **读取市场深度**：市场深度为0
          	    1. **读取我的成交**: 应该为空
          	    1. **读取市场成交**： 应该为空
          	    1. **读取我的账号**: LRC 可用余额应为1000

        1. 发送CancelOrder请求，请求参数owner：A1，marketPair：GTO-WETH
        	- 结果验证：
        		1. **返回结果**：ERR_ORDER_NOT_EXIST

    - **状态**: Planned

    - **拥有者**: 杜永丰

    - **其他信息**：NA
    
1. **测试CutoffEvent取消owner订单流程**

    - **Objective**：收到CutoffEvent可以正确取消订单

    - **测试设置**：

        1. 设置A1账号有1000个LRC，授权充足
        1. 在LRC-WETH市场下一个卖10个LRC的单，价格是0.01WETH，返回orderHash：O1
        1. 在GTO-WETH市场下一个卖10个GTO的单，价格是0.001WETH，返回orderHash：O2
        1. 发送CutoffEvent，参数broker=A2, owner=A2
            - 结果验证：
                1. **读取我的订单**：通过getOrders应该看到该订单，其中的status应该为STATUS_PENDING
                1. **读取市场深度**：两个市场分别有10个卖单深度
                1. **读取我的成交**: 无
                1. **读取市场成交**：无
                1. **读取我的账号**: LRC 可用余额应为990,GTO 可用余额应为990
        1. 发送CutoffEvent，参数broker=A1, owner=A1
            - 结果验证：
                1. **读取我的订单**：通过getOrders应该看到该订单，其中的status应该为STATUS_ONCHAIN_CANCELLED_BY_USER
                1. **读取市场深度**：2个市场卖单深度应该变为0
                1. **读取我的成交**: 无
                1. **读取市场成交**：无
                1. **读取我的账号**: LRC 可用余额应为1000,GTO 可用余额应为1000

    - **状态**: Planned

    - **拥有者**: 杜永丰

    - **其他信息**：NA
    
1. **测试CutoffEvent取消owner-market订单流程**

    - **Objective**：收到CutoffEvent可以正确取消订单

    - **测试设置**：

        1. 设置A1账号有1000个LRC，授权充足
        1. 在LRC-WETH市场下一个卖10个LRC的单，价格是0.01WETH，返回orderHash：O1
        1. 在GTO-WETH市场下一个卖10个GTO的单，价格是0.001WETH，返回orderHash：O2
        1. 发送CutoffEvent，参数broker=A1, owner=A1, marketHash=ZRX-WETH
            - 结果验证：
                1. **读取我的订单**：通过getOrders应该看到该订单，其中的status应该为STATUS_PENDING
                1. **读取市场深度**：两个市场分别有10个卖单深度
                1. **读取我的成交**: 无
                1. **读取市场成交**：无
                1. **读取我的账号**: LRC 可用余额应为990,GTO 可用余额应为990
        1. 发送CutoffEvent，参数broker=A1, owner=A1, marketHash=LRC-WETH
            - 结果验证：
                1. **读取我的订单**：通过getOrders应该看到该订单，其中LRC-WETH的status应该为STATUS_ONCHAIN_CANCELLED_BY_USER,
                GTO-WETH的status应该为STATUS_PENDING
                1. **读取市场深度**：LRC-WETH深度变为0，GTO-WETH 10个卖单
                1. **读取我的成交**: 无
                1. **读取市场成交**：无
                1. **读取我的账号**: LRC 可用余额应为1000,GTO 可用余额应为990

    - **状态**: Planned

    - **拥有者**: 杜永丰

    - **其他信息**：NA
    
1. **测试OrdersCancelledOnChainEvent取消订单流程**

    - **Objective**：收到OrdersCancelledOnChainEvent可以正确取消订单

    - **测试设置**：

        1. 设置A1账号有1000个LRC，授权充足
        1. 在LRC-WETH市场下一个卖10个LRC的单，价格是0.01WETH，返回orderHash：O1
        1. 在GTO-WETH市场下一个卖10个GTO的单，价格是0.001WETH，返回orderHash：O2
        1. 发送OrdersCancelledOnChainEvent，参数Seq(O1, O2)
            - 结果验证：
                1. **读取我的订单**：通过getOrders应该看到该订单，其中的status应该为STATUS_ONCHAIN_CANCELLED_BY_USER
                1. **读取市场深度**：2个市场卖单深度应该变为0
                1. **读取我的成交**: 无
                1. **读取市场成交**：无
                1. **读取我的账号**: LRC 可用余额应为1000,GTO 可用余额应为1000

    - **状态**: Planned

    - **拥有者**: 杜永丰

    - **其他信息**：NA
    
---

###  <a name="partially"></a> STATUS_PARTIALLY_FILLED状态订单的取消

1. **测试按orderHash取消订单流程**

    - **Objective**：测试按orderHash可以正确取消订单，不存在的orderHash会返回错误码

    - **测试设置**：

        1. 设置账号有1000个LRC，授权充足
        1. 在LRC-WETH市场下一个卖100个LRC的单，价格是0.01WETH，返回orderHash：O1
        1. 发送OrderFilledEvent事件，模拟成交1个LRC
            - 结果验证：
                1. **读取我的订单**：通过getOrders查询status应该为STATUS_PARTIALLY_FILLED
                1. **读取市场深度**：卖单深度应该变为99
                1. **读取我的成交**: 有1LRC卖单成交
                1. **读取市场成交**： 有1LRC卖单成交
                1. **读取我的账号**: LRC 余额应为999, 可用余额900

        1. 发送CancelOrder请求，请求参数orderHash：O1，status：STATUS_SOFT_CANCELLED_BY_USER
            - 结果验证：
          	    1. **返回结果**：ERR_NONE
          	    1. **读取我的订单**：通过getOrders查询status应该为STATUS_SOFT_CANCELLED_BY_USER
          	    1. **读取市场深度**：卖单深度应该变为0
          	    1. **读取我的成交**: 有1LRC卖单成交
          	    1. **读取市场成交**：有1LRC卖单成交
          	    1. **读取我的账号**: LRC 余额应为999, 可用余额999

        1. 发送CancelOrder请求，请求参数O2，STATUS_SOFT_CANCELLED_BY_USER

        	- 结果验证：
        		1. **返回结果**：ERR_ORDER_NOT_EXIST

    - **状态**: Planned

    - **拥有者**: 杜永丰

    - **其他信息**：NA
    
---
###  <a name="onchain_cancelled"></a> STATUS_ONCHAIN_CANCELLED_BY_USER状态订单的取消

1. **测试按orderHash取消订单流程**
    - **Objective**：测试按orderHash不应该更改订单状态

    - **测试设置**：
        1. 设置账号有1000个LRC，授权充足
        1. 在LRC-WETH市场下一个卖100个LRC的单，价格是0.01WETH，返回orderHash：O1
        1. 发送OrdersCancelledOnChainEvent事件，传入orderHash：O1
            - 结果验证：
                1. **读取我的订单**：通过getOrders查看订单status应该为STATUS_ONCHAIN_CANCELLED_BY_USER
                1. **读取市场深度**：卖单深度应该变为0
                1. **读取我的成交**:  无
                1. **读取市场成交**：无
                1. **读取我的账号**: LRC 可用余额应为1000

        1. 发送CancelOrder请求，请求参数orderHash：O1，status：STATUS_SOFT_CANCELLED_BY_USER
            - 结果验证：
                1. **返回结果**：应该返回错误，目前代码未处理
        1. 发送CancelOrder请求，请求参数O2，STATUS_SOFT_CANCELLED_BY_USER
        	- 结果验证：
        		1. **返回结果**：ERR_ORDER_NOT_EXIST

    - **状态**: Planned

    - **拥有者**: 杜永丰

    - **其他信息**：NA
