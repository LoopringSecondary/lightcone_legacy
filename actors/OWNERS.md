# 主要分工

- 红雨:
    - OrderHandlerActor
    - OrderbookManagerActor
    - MarketManagerActor
    - AccountManagerActor
    - all deployment and docker

- 永丰：
    - TokenMetadataActor
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

