## 以太坊事件测试用例

### Internal Ticker 测试

1. inter ticker的解析和推送

   - 目标：测试订单成交后，internal ticker的解析和推送

   - 测试前置条件：

     1. 设置订单A 卖出100 LRC 买入 1WETH，fee 为1LRC
     2. 设置订单B 卖出1WETH 买入 100LRC，fee为1LRC

   - 测试步骤和结果验证：

     1. 发送A、B的订单

     2. 发出ohlcdata

        ==>  验证 socket推送一条internal ticker，last price 为0.01

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：NA

### Block Gas Price 测试

1. 测试block gas price 事件

   - 目标：测试 block gas price事件对系统推荐gas price影响

   - 测试前置条件：

     1. 设置一个BlockGasPricesExtractedEvent，height设置为0，gas price 设置100个，设置5个gas Price为20Gwei，设置30个15 Gwei，设置30个 10Gwei 设置 30个5Gwei，设置5个1Gwei。

   - 测试步骤及结果验证：

     1. 将BlockGasPricesExtractedEvent发送到GasPriceActor 

        ==> 验证系统推荐的gasPrice更新为10Gwei。

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：暂时不考虑gas price降低引发rematch， rematch采用定时尝试。

### Activity 

1. Activity 状态更新
    - **Objective**：测试pending的activity受nonce和block的影响
    - **测试设置**：
        1. dispatch两个Activity，第一个的hash为0x001,nonce为10,第二个的为0x002，nonce为10
        2. dispatch一个block事件，包含第二个activity
        1. dispatch第二个Activity，但是设置block和txStatus
    - **结果验证**：
        1. **读取我的活动**：应当只包含第二个Activity并且状态为"TX_STATUS_SUCCESS"
    - **状态**: Planned
    - **拥有者**: 红雨
    - **其他信息**：NA
    
2. Activity 被删除

   - 目标：测试pending的activity在后续相同nonce不同activity的影响

   - 测试前置条件:

     1. 设置一个pending 的transfer eth out 的activity，nonce 为10  —  a1
     2. 设置一个success 的transfer LRC out 的activity，nonce 为10  —  a2

   - 测试步骤及结果验证：

     1. 发出 a1

     2. 发出a2

        ==> 验证 activity a1被删除， a2正确存储

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：NA

### K线数据

1. 测试ohlc data 

   - 目标：测试ohlc data能否正确存储，正确合并

   - 测试前置条件：

     1. 设置block = 96,  ohlcdata 100 LRC -> 0.1WETH  时间为t1
     2. 设置block = 101,  ohlcdata 150 LRC -> 0.2WETH  时间为t1 +75s
     3. 设置block = 102,  ohlcdata 150 LRC -> 0.2WETH  时间为t1 +90s

   - 测试步骤及结果校验：

     1. 发送上面三条 ohlcdata

     2. 请求 lrc-weth 市场，interval为1分钟，时长1小时的K线数据

        ==> 验证是不是两条数据，分别为100 LRC 和300 LRC

   - 状态: Planned

   - 拥有者: 亚东

   - 其他信息：NA


### RingMinedEvent

1. 测试成功以及失败的，对AccountManager、OrderbookManager以及MarketManager的影响

1. 失败多次之后，需要将对应订单删除
    - **Objective**：测试当一个订单被多次提交环路之后，都遇到错误无法执行成功，认为该订单有问题，将其删除
    - **测试设置**：
        1. 设置一个新账号，有足够的余额和授权
        1. 下一个卖出dynamicMarketPair.baseToken的订单
        1. 下一个买入的订单，并且使之能成交，并提交环路
        1. dispatch失败的RingMinedEvent
        1. 重复提交订单一和dispatch失败的RingMinedEvent，直到达到配置里的次数
    - **结果验证**：
        1. **读取我的订单**：该订单的状态应该为 `STATUS_SOFT_CANCELLED_TOO_MANY_RING_FAILURES`
        1. **读取市场深度**：为空
        1. **读取我的成交**: 为空
        1. **读取市场成交**：为空
        1. **读取我的账号**: 余额和授权为初始值
    - **状态**: Planned
    - **拥有者**: 红雨
    - **其他信息**：NA
    

### OrderFilledEvent
--- 
分为2种情况：部分成交和完全成交

---    
1. 部分成交时
    - **Objective**：测试一个订单收到部分成交的事件时，需要完成以下事件：
                1、账户可用金额增加订单的金额(因为可用金额的减少是在UpdatedEvent中完成的)
                2、订单的状态在未到灰尘单时，仍为STATUS_PENDING，但是outStandingAmountS需要改变事件的金额，
                3、深度图减去事件的金额
    - **测试设置**：
        1. 设置一个新账号，有足够的余额和授权
        1. 下一个卖出dynamicMarketPair.baseToken的订单
        1. dispatch一个部分成交的FillEvent事件
        1. 等待执行完毕
    - **结果验证**：
        1. **读取我的订单**：该订单的状态应该为 `STATUS_PENDING`
        1. **读取市场深度**：订单金额 减去 filledEvent的金额
        1. **读取我的成交**: 为空(因为未触发Fill事件)
        1. **读取市场成交**：为空(因为未触发Fill事件)
        1. **读取我的账号**: 余额和授权为初始值(未发送BalanceUpdatedEvent)，可用金额 - 订单的剩余金额
    - **状态**: Planned
    - **拥有者**: 红雨
    - **其他信息**：NA
    
1. 完全成交时
    - **Objective**：测试一个订单收到完全成交的事件时，需要完成以下事件：
                1、账户可用金额增加订单的金额(因为可用金额的减少是在UpdatedEvent中完成的)
                2、订单的状态为STATUS_PENDING，outStandingAmountS需要改变事件的金额，
                3、深度图减去事件的金额
    - **测试设置**：
        1. 设置一个新账号，有足够的余额和授权
        1. 下两个个卖出dynamicMarketPair.baseToken的订单
        1. dispatch两个FilledEvent事件，一个完全成交、一个减去完成成交之后为灰尘单的
        1. 等待执行完毕
    - **结果验证**：
        1. **读取我的订单**：订单的状态应该为 `STATUS_COMPLETELY_FILLED`
        1. **读取市场深度**：为空
        1. **读取我的成交**: 为空(因为未触发Fill事件)
        1. **读取市场成交**：为空(因为未触发Fill事件)
        1. **读取我的账号**: 余额和授权为初始值(未发送BalanceUpdatedEvent)，可用金额也为初始值
    - **状态**: Planned
    - **拥有者**: 红雨
    - **其他信息**：NA