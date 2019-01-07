## 路线图

我们把 lightcone 的开发分为以下几个阶段。重要的一点是：每个阶段都应该可以把系统部署运行起来，好让我们更理解每个阶段的系统设计状态。


- **v0.1**(目标12月底): 主要功能包括：
  - [x] [yadong] 连接到以太坊节点，并且可以读取 1）账户ETH和各种token的余额以及Allowance，2）某个特定订单的成交金额；
  - [x] [hongyu] 通过上述以太坊链接，动态创建和初始化AccountManangerActor
  - [x] [hongyu/wangdong]提交和取消订单的整个流程（忽略订单生效，过期时间，忽略cutofff），以及市场深度读取，基本的撮合
  - [x] [kongliang] 环路生成，签名，和以太坊的提交，（但不包括以太坊环路提交结果的处理）
  - [x] [yongfeng] 用户订单数据的读取，成交的读取（目前应该是空结果）
  - [x] [yadong] ETH和WETH的转换，以及setCutoff, 和cancelOrder(软取消和硬取消）
  - [x] [wangdong] gateway http链接


- **v0.2** (目标1月15号）主要功能包括
  - [x] [wangdong/yongfeng] 基于订单数据库表的actor recover操作
  - [x] [hongyu] 订单生效，过期处理（actor层面）
  - [x] [wangdong] 解决订单本（深度）问题
  - [ ] [hongyu] cutoff的处理（actor层面）
  - [ ] [yadong/yongfeng] 以太坊上面爬取存储到数据库。主要包括ring，ERC20 transfer, Ether Transfer, trade，cutoff的存储。并基于此提供更多用户读取数据的API
  - [ ] [yadong/hongyu] **以太坊事件的解析和通知（但不包括分叉的处理）**
  - [ ] [hongyu] **以太坊事件处理（包括actor里面的处理，和数据库的一些更改操作）**
  - [ ] [hongyu] TokenMetadataRefresher启动以后应该自己询问费用代币的burn rate


- **v0.3** (目标1月末）主要功能包括
  - [ ] [wangdong] 支持websocket
  - [ ] [yadong/hongyu] 以太坊分叉事件的处理
  - [ ] [yadong] JSONRPC支持HTTPS
  - [ ] [yadong] Akka Cluster支持SSL/TLS
  - [ ] [yongfeng] 所有和市场，包括orderbook相关的配置，应该都放到数据库里面，从配置文件里面移除。所有Token的信息也应该存入数据库。


- **v0.4** 主要功能包括
  - [ ] 更好的整集群集成测试
  - [ ] [yongfeng]redis缓存
  - [ ] 清理每个人的TODO
  - [ ] 集群监控
  - [ ] 动态增加token和市场，动态停止某个市场（配置放入数据库）
  - [ ] kongliang/yadong: 整理API文档，中文即可。包括json schema和json rpc。
  - [ ] 特殊数据可用来存储k线

