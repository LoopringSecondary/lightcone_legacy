## 以太坊事件测试用例

### Interal Ticker 测试

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
   - 其他信息：推荐的gas price降低应该可以引发原本没有收益的订单变得有收益，从而可以重新加入撮合队列中，这样就触发了rematch事件。rematch事件同样可能由token burn rate事件触发，因为在token burn rate测试中一并进行。