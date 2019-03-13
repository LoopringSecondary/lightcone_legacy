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
 
     - **状态**: Planned
 
     - **拥有者**: 杜永丰
 
     - **其他信息**：NA
     
 1. **测试market metadata变动，新增市场流程**
      - **Objective**：测试更新db里的market metadata，通过keepAliveActor的激活是否能新增一个市场
  
      - **测试设置**：
  
          1. 在tokenMetadata里新增一个token配置 ZRX
          1. 依次启动ExternalTicker，MetadataManagerActor，MetadataRefresher
            - 结果验证：
                1. **给market发消息**：给ZRX-WETH marketActor shard（通过entityId查找shard）发消息，不会正常返回
                1. **给orderbook发消息**：给ZRX-WETH orderbookActor shard（通过entityId查找shard）发消息，不会正常返回
          1. 调用TokenBurnRateChangedEvent接口更新GTO的metadata，burnRateForP2P从0.0到0.5，为了触发tokens重新加载。
          调用SaveMarketMetadatas接口新增ZRX-WETH market配置
          1. 等待3s，确定已经同步完成
            - 结果验证：
                1. **给market发消息**：给新增的ZRX-WETH marketActor shard（通过entityId查找shard）发消息，应正常返回
                1. **给orderbook发消息**：给新增的ZRX-WETH orderbookActor shard（通过entityId查找shard）发消息，应正常返回
  
      - **状态**: Planned
  
      - **拥有者**: 杜永丰
  
      - **其他信息**：NA

 1. **测试market metadata变动，终结市场流程**
       - **Objective**：测试更新db里的market metadata，通过marketManager接收MetadataChanged是否能终结一个市场
   
       - **测试设置**：
   
           1. 依次启动ExternalTicker，MetadataManagerActor，MetadataRefresher
             - 结果验证：
                 1. **给market-shard发消息**：给GTO-WETH marketActor shard（通过entityId查找shard）发消息，应返正常返回
                 1. **给orderbook发消息**：给GTO-WETH orderbookActor shard（通过entityId查找shard）发消息，应返正常返回
           1. 调用TerminateMarket接口更新GTO-WETH市场为终结状态
           1. 等待3s，确定已经同步完成
             - 结果验证：
                 1. **给market-shard发消息**：给GTO-WETH marketActor shard（通过entityId查找shard）发消息，不应返回消息
                 1. **给orderbook发消息**：给GTO-WETH orderbookActor shard（通过entityId查找shard）发消息，不应返回消息
   
       - **状态**: Planned
   
       - **拥有者**: 杜永丰
   
       - **其他信息**：NA