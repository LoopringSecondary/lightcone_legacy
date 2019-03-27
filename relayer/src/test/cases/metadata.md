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
                1. **验证orderbook**：orderbook正常为提交的订单的金额
                1. **验证MarketManagerActor**：提交一个该市场的订单，应该无法提交，返回INVALID_MARKET
                1. **socket推送**：socket收到MetadataChanged事件
    
    - **状态**: Planned
    
    - **拥有者**: 杜永丰、于红雨
    
    - **其他信息**：可参考原测试  DynamicAdjustMarketsSpec
    
 1. **测试market metadata变动，市场由active->terminated->active的流程**
     - **Objective**：测试市场dynamicMarketPair变成Terminated，再恢复成Avtivity时，orderbook等正常
     
     - **测试设置**：
         1. 提交一个dynamicMarketPair的市场订单
         1. 将db中db中dynamicMarketPair的状态更改为Terminated
         1. 等待n秒，确保MetadataManagerActor同步完成
            - 结果验证：
                1. **验证orderbook**：获取orderbook时，返回错误，INVALID_MARKET
                1. **socket推送**：socket收到MetadataChanged事件
         1. 将db中db中dynamicMarketPair的状态更改为Activity
         1. 等待n秒，确保MetadataManagerActor同步完成
                     - 结果验证：
                         1. **验证orderbook**：获取orderbook时，应该为提交的订单的金额
                         1. **验证MarketManger**：提交一个该市场的订单，并返回成功
                         1. **socket推送**：socket收到MetadataChanged事件
     
     - **状态**: Planned
     
     - **拥有者**: 杜永丰、于红雨
     
     - **其他信息**：NA