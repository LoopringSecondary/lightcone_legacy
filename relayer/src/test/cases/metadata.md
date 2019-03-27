## Metadata变动

测试爬取的CMC ticker和Sina的法币兑换率后的影响，包括ticker价格和metadata变动对市场的影响

 ### Ticker和metadata信息改变
 
 1. **测试通过数据库进行变动流程**
     - **Objective**：测试更新db里的TokenMetaData、TokenInfo、TokenTicker以及MarketMetadata，定时同步之后，会刷新数据
 
     - **测试设置**：
         1. 设置定时同步时间为5s，
         1. 修改GTO的metadata.burnrate "0.2"、tokeninfo.circulatingSupply为"100000000"、tokenticker.price为"0.04", 以及GTO-WETH的metadata.status=Terminate
         1. 等待8s，确保同步完成
            - 结果验证：
                1. **读取tokens**：通过GetTokens可以查到GTO的burnRateForP2P为0.2，circulatingSupply为"100000000"，price为"0.04"
                1. **市场状态**：提交一个GTO-WETH的订单，并且返回INVALID_MARKET的错误
                1. **socket推送**：socket收到MetadataChanged事件
 
     - **状态**: Planned
 
     - **拥有者**: 于红雨
 
     - **其他信息**：N/A
     
 1. **测试market metadata变动，新增市场流程**
     - **Objective**：测试在db里增加一个新的market metadata，然后激活该市场的MarketManager和OrderbookManager，再提交该市场的订单，验证是否添加成功
  
     - **测试设置**：
  
         1. 在MarketMetadata里增加一个market metadata, dynamicBaseToken -> WETH
         1. 等待n秒，确保MetadataManagerActor同步完成
         1. 激活新添加的市场
         1. 提交一个该市场的卖单
            - 结果验证：
                1. **读取我的订单**：通过getOrders应该看到该订单，其中的status应该为STATUS_PENDING
                1. **读取市场深度**：卖单深度为该订单的大小
                1. **socket推送**：socket收到MetadataChanged事件
  
     - **状态**: Planned
  
     - **拥有者**: 杜永丰、于红雨
  
     - **其他信息**：NA

 1. **测试market metadata变动，终止市场流程**
    - **Objective**：测试在db中将一个市场状态更新为Terminated，然后等待MetadataManagerActor重新同步一次之后，该市场应该被终止了，无法执行提交订单等操作
   
    - **测试设置**：
   
        1. 提交一个dynamicMarketPair市场的订单，确认该市场正常可用
        1. 在db中将dynamicMarketPair的状态更改为Terminated
        1. 等待n秒，确保MetadataManagerActor同步完成
            - 结果验证：
                1. **MarketManagerActor状态**：dynamicMarketPair市场的MarketManagerActor 和OrderbookManagerActor都已经停止
                1. **提交订单**：提交该市场的订单时，无法提交成功，返回错误 ERR_INVALID_MARKET
                1. **orderbook**：请求该市场的orderbook时，无法成功，返回错误 ERR_INVALID_MARKET
                1. **socket推送**：socket收到MetadataChanged事件
   
    - **状态**: Planned
   
    - **拥有者**: 杜永丰、于红雨
   
    - **其他信息**：NA
       
 1. **测试market metadata变动，市场变为readonly的流程**
    - **Objective**：测试修改db中dynamicMarketPair的状态为Readonly，然后验证提交订单以及获取深度等逻辑
    
    - **测试设置**：
    
        1. 将db中dynamicMarketPair的状态更改为Readonly
        1. 等待n秒，确保MetadataManagerActor同步完成
        1. 提交一条该市场的订单
        1. 等待3s，确定已经同步完成
            - 结果验证：
                1. **给accountManager发消息**：给GTO-WETH 发提交订单请求，返回异常
                1. **给market-shard发消息**：给GTO-WETH marketActor 发MetadataChanged，返回异常
                1. **给orderbook发消息**：给GTO-WETH orderbookActor 发MetadataChanged，返回异常
    
    - **状态**: Planned
    
    - **拥有者**: 杜永丰、于红雨
    
    - **其他信息**：可参考原测试  DynamicAdjustMarketsSpec
    
 1. **测试market metadata变动，市场由active->terminated->active的流程**
     - **Objective**：测试市场从有效状态变为无效，再恢复成有效的情况
     
     - **测试设置**：
         1. 设置A1账号有1000个LRC，授权充足
         1. 依次启动ExternalTicker，MetadataManagerActor，MetadataRefresher
             - 结果验证：
                 1. **给market-shard发消息**：给LRC-WETH marketActor发消息，应返正常返回
                 1. **给orderbook发消息**：给LRC-WETH orderbookActor发消息，应返正常返回
         1. 在LRC-WETH市场下一个卖10个LRC的单，价格是0.01WETH，返回orderHash：O1
             - 结果验证：
                 1. **读取我的订单**：通过getOrders应该看到该订单，其中的status应该为STATUS_PENDING
                 1. **读取市场深度**：在LRC-WETH市场有10个卖单深度
                 1. **读取我的成交**: 无
                 1. **读取市场成交**：无
                 1. **读取我的账号**: LRC 可用余额应为990
         1. 调用UpdateMarketMetadata，参数(MarketMetadata(market=LRC-WETH, status=TERMINATED))
         1. 等待3s，确定已经同步完成
             - 结果验证：
                 1. **读取我的订单**：通过getOrders应该看到该订单，其中的status应该为STATUS_SOFT_CANCELLED_BY_DISABLED_MARKET
                 1. **读取市场深度**：在LRC-WETH市场有查询深度会报错
                 1. **提交订单**：在LRC-WETH市场发提交订单请求，返回异常
                 1. **给market-shard发消息**：给LRC-WETH marketActor 发MetadataChanged，返回异常
                 1. **给orderbook发消息**：给LRC-WETH orderbookActor 发MetadataChanged，返回异常
         1. 调用UpdateMarketMetadata，参数(MarketMetadata(market=LRC-WETH, status=ACTIVE))
         1. 等待3s，确定已经同步完成
         1. 在LRC-WETH市场下一个卖10个LRC的单，价格是0.01WETH，返回orderHash：O1
             - 结果验证：
                 1. **读取我的订单**：通过getOrders应该看到该订单，其中的status应该为STATUS_PENDING
                 1. **读取市场深度**：在LRC-WETH市场有10个卖单深度
                 1. **读取我的成交**: 无
                 1. **读取市场成交**：无
                 1. **读取我的账号**: LRC 可用余额应为990
     
     - **状态**: Planned
     
     - **拥有者**: 杜永丰、于红雨
     
     - **其他信息**：NA