## Metadata变动

测试爬取的CMC ticker和Sina的法币兑换率后的影响，包括ticker价格和metadata变动对市场的影响

 ### Ticker和metadata信息改变

 1. **测试爬取CMC ticker，Sina法币兑换率流程**
    - **Objective**：测试从CMC爬取的ticker信息和从Sina爬取的对美元兑换率正确的存入db，并更新metadataManager对象

    - **测试设置**：
        
        1. 配置token_symbol_slug支持BTC, ETH, LRC, GTO, WETH。要获取RMB,JPY,EUR,GBP对美元的汇率。
        市场配置为LRC-WETH,GTO-WETH
        1. 准备一份mock的cmc和sina数据，实现mock的cmc和sina两个fetcher，给ExternalTickerActor注入mock的实现
        1. 依次启动ExternalTicker，MetadataManagerActor，MetadataRefresher
        	- 结果验证：
          	    1. **读取tokens**：通过GetTokens可以查到 ETH, LRC, GTO, WETH的ticker
          	    1. **读取markets**：通过GetMarkets可以查到 LRC-WETH,GTO-WETH

    - **状态**: Planned

    - **拥有者**: 杜永丰

    - **其他信息**：NA

 1. **测试token metadata变动流程**
     - **Objective**：测试更新db里的token metadata，是否能在下一次定时同步时加载到最新变动
 
     - **测试设置**：
         1. GTO的metadata，burnRateForP2P初始值0.0
         1. 依次启动ExternalTicker，MetadataManagerActor，MetadataRefresher
         1. 调用TokenBurnRateChangedEvent接口更新GTO的metadata，burnRateForP2P从0.0到0.5
         1. 等待3s，确定已经同步完成
            - 结果验证：
                1. **读取tokens**：通过GetTokens可以查到GTO的burnRateForP2P为0.5
                1. **socket推送**：socket收到MetadataChange(tokenMetadataChanged)
 
     - **状态**: Planned
 
     - **拥有者**: 杜永丰
 
     - **其他信息**：可参考原测试 MetadataManagerSpec
     
 1. **测试market metadata变动，新增市场流程**
     - **Objective**：测试更新db里的market metadata，通过keepAliveActor的激活是否能新增一个市场
  
     - **测试设置**：
  
         1. 在tokenMetadata里新增一个token配置 ZRX
         1. 依次启动ExternalTicker，MetadataManagerActor，MetadataRefresher
            - 结果验证：
                1. **给market发消息**：给ZRX-WETH marketActor发消息，返回异常
                1. **给orderbook发消息**：给ZRX-WETH orderbookActor发消息，返回异常
         1. 调用TokenBurnRateChangedEvent接口更新GTO的metadata，burnRateForP2P从0.0到0.5，为了触发tokens重新加载。
          调用SaveMarketMetadatas接口新增ZRX-WETH market配置
         1. 等待3s，确定已经同步完成
            - 结果验证：
                1. **给market发消息**：给新增的ZRX-WETH marketActor发消息，应正常返回
                1. **给orderbook发消息**：给新增的ZRX-WETH orderbookActor发消息，应正常返回
                1. **socket推送**：socket收到MetadataChange(tokenMetadataChanged) MetadataChange(marketMetadataChanged)
  
     - **状态**: Planned
  
     - **拥有者**: 杜永丰
  
     - **其他信息**：NA

 1. **测试market metadata变动，终结市场流程**
    - **Objective**：测试更新db里的market metadata，通过marketManager接收MetadataChanged是否能终结一个市场
   
    - **测试设置**：
   
        1. 依次启动ExternalTicker，MetadataManagerActor，MetadataRefresher
            - 结果验证：
                1. **给market-shard发消息**：给GTO-WETH marketActor发消息，应返正常返回
                1. **给orderbook发消息**：给GTO-WETH orderbookActor发消息，应返正常返回
        1. 调用TerminateMarket接口更新GTO-WETH市场为终结状态
        1. 等待3s，确定已经同步完成
            - 结果验证：
                1. **给market-shard发消息**：给GTO-WETH marketActor发消息，返回异常
                1. **给orderbook发消息**：给GTO-WETH orderbookActor发消息，返回异常
                1. **socket推送**：socket收到MetadataChange(marketMetadataChanged)
   
    - **状态**: Planned
   
    - **拥有者**: 杜永丰
   
    - **其他信息**：NA
       
 1. **测试market metadata变动，市场变为readonly的流程**
    - **Objective**：测试更新db里的market metadata，通过marketManager接收MetadataChanged是否能更新一个市场为readonly状态
    
    - **测试设置**：
    
        1. 依次启动ExternalTicker，MetadataManagerActor，MetadataRefresher
            - 结果验证：
                1. **给market-shard发消息**：给GTO-WETH marketActor发消息，应返正常返回
                1. **给orderbook发消息**：给GTO-WETH orderbookActor发消息，应返正常返回
        1. 调用UpdateMarketMetadata，参数(MarketMetadata(market=GTO-WETH, status=READONLY))
        1. 等待3s，确定已经同步完成
            - 结果验证：
                1. **给accountManager发消息**：给GTO-WETH 发提交订单请求，返回异常
                1. **给market-shard发消息**：给GTO-WETH marketActor 发MetadataChanged，返回异常
                1. **给orderbook发消息**：给GTO-WETH orderbookActor 发MetadataChanged，返回异常
    
    - **状态**: Planned
    
    - **拥有者**: 杜永丰
    
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
     
     - **拥有者**: 杜永丰
     
     - **其他信息**：NA