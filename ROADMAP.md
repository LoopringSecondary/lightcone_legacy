## 路线图

我们把 lightcone 的开发分为以下几个阶段。重要的一点是：每个阶段都应该可以把系统部署运行起来，好让我们更理解每个阶段的系统设计状态。


- **v0.1**(目标12月底): 主要功能包括：
  - [yadong] 连接到以太坊节点，并且可以读取 1）账户ETH和各种token的余额以及Allowance，2）某个特定订单的成交金额；
  - [hongyu] 通过上述以太坊链接，动态创建和初始化AccountManangerActor
  - [hongyu/wangdong]提交和取消订单的整个流程（忽略订单生效，过期时间，忽略cutofff），以及市场深度读取，基本的撮合
  - [kongliang] 环路生成，签名，和以太坊的提交，（但不包括以太坊环路提交结果的处理）
  - [yongfeng] 用户订单数据的读取，成交的读取（目前应该是空结果）
  - [yadong] ETH和WETH的转换，以及setCutoff, 和cancelOrder(软取消和硬取消）
  - [wangdong] gateway http链接



- **v0.2** (目标1月15号）主要功能包括
  - [yadong/yongfeng] 以太坊上面环路的爬取和存储到数据库。主要包括ring，trade，cutoff的存储。并基于此提供更多用户读取数据的API
  - [yadong/hongyu] 以太坊事件的解析和通知（但不包括分叉的处理）
  - [hongyu] 以太坊事件处理（包括actor里面的处理，和数据库的一些更改操作）
  - [wangdong/yongfeng] 基于订单数据库表的actor recover操作


- **v0.3** (目标1月低）主要功能包括
  - [yadong/hongyu] 以太坊分叉事件的处理
  - [yongfeng/wangdong] 订单生效，过期，和cutoff的处理（actor层面）
  - [wangdong]redis缓存
  - [wangdong] gateway restful链接


- **v0.4** 主要功能包括
  - 性能优化
  - 动态增加token和市场，动态停止某个市场

