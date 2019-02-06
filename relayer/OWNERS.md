# 主要分工

- 红雨:
    - OrderHandlerActor
    - OrderbookManagerActor
    - MarketManagerActor
    - AccountManagerActor
    - BadMessageListener
    - all deployment and docker

- 永丰：
    - TokenMetadataRefresher
    - OrderRecoverActor
    - EthereumEventPersistorActor
    - DatabaseQueryActor
    - All database tabels and dals

- 亚东：
    - RingSettlementActor
    - EthereumEventExtractorActor
    - OrderHistoryActor
    - AccountBalanceActor
    - GasPriceActor
    - ethereum encoding/declding library

