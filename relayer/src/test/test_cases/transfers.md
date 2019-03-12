## 转账事件测试用例

主要测试ETH转账，ERC20 token 转账，WETH的wrap 和 unwrap事件，系统的反应正确与否。转账分为转出和转入。转账事件会对order的可成交量产生影响，从而影响order book。因此这次还需要覆盖到转账事件对order和orderbook的影响

- ### ETH 转账

  - ##### ETH转账成功
<table>
  <tr>
    <th>前置条件</th>
    <th>步骤</th>
    <th>预期输出结果</th>
  </tr>
  <tr>
    <td rowspan="4">A账户余额 20 ETH；<br>B账户余额 不限制</td>
    <td>发送A转账到B的Ethereum Transaction</td>
    <td></td>
  </tr>
  <tr>
    <td>发出A和B的pending 转账 Activity</td>
    <td>db 存入一条A的转出 10 ETH 的pending Activity；<br>db 存入一条B的转出10 ETH的pending Activity；<br>socket 推送A 的pending Activity 给A;<br>socket 推送B 的 pending Activity给B；<br><br>A，B的ETH余额没有变化</td>
  </tr>
  <tr>
    <td>发出A和B的成功的转账Activity</td>
    <td>db删除上面两条Activity，重新存入两条成功的转账Activity；<br>socket推送成功的转出10 ETH的Activity给A；<br>socket推送成功的转入10 ETH的Activity给B；</td>
  </tr>
  <tr>
    <td>发出A的AddressBalanceUpdated事件<br>和B的AddressBalanceUpdated事件</td>
    <td>socket 推送给A包含A最新ETH余额的Account事件；<br>socket推送给B包含B最新ETH余额的Account事件；<br>A的ETH余额为 10ETH - 油费，B的余额 + 10ETH</td>
  </tr>
</table>

  - ##### ETH转账失败

<table>
  <tr>
    <th>前置条件</th>
    <th>步骤</th>
    <th>预期输出结果</th>
  </tr>
  <tr>
    <td rowspan="3">设置A的ETH余额为20 ETH；<br>B的余额不限制</td>
    <td>设置Tx的gas Limit&nbsp;&nbsp;为10000，<br>生成一个A转10 ETH到B的Ethereum transaction,<br>发送该 transaction </td>
    <td></td>
  </tr>
  <tr>
    <td>发出A和B的pending 转账 Activity</td>
    <td>db 存入一条A的转出 10 ETH 的pending Activity；<br>db 存入一条B的转出10 ETH的pending Activity；<br>socket 推送A 的pending Activity 给A; <br>socket 推送B 的 pending Activity给B；<br>A，B的ETH余额没有变化</td>
  </tr>
  <tr>
    <td>发出A和B的失败转账 Activity</td>
    <td>db删除上面两条Activity，重新存入两条失败的转账Activity；<br>socket推送失败的转出10 ETH的Activity给A；<br>socket推送失败的转入10 ETH的Activity给B；<br>A的ETH余额减少油费，B的余额没有发生变化；</td>
  </tr>
</table>


- ### WETH wrap 与unwrap

  - ##### 场景：没有卖出WETH或者作为手续费的相关订单

    | 分支        | 步骤           | 预期输出结果                                                 |
    | ----------- | -------------- | ------------------------------------------------------------ |
    | wrap 成功   | wrap 10 WETH   | ETH 和WETH各有一个成功的 wrap Acitivity；<br />ETH减少 10 +油费；WETH增加10 |
    | wrap 失败   | wrap 10 WETH   | ETH 和WETH各有一个失败的 wrap Acitivity；<br />ETH减少油费，WETH余额不变 |
    | unwrap成功  | unwrap 10 WETH | ETH 和WETH 各有一个成功的unwrap Activity；<br />ETH 增加10 -油费，WETH减少10 |
    | unwrap 失败 | unwrap 10 WETH | ETH 和WETH各有一个失败的 unwrap Acitivity；<br /> ETH 减少 油费，WETH 余额不变 |

  - ##### 场景：有卖出WETH的相关订单

    | 分支                                    | 步骤           | 预期输出结果                                                 |
    | --------------------------------------- | -------------- | ------------------------------------------------------------ |
    | wrap 成功                               | wrap 10 WETH   | ETH 和WETH各有一个成功的 wrap Acitivity；ETH减少 10 +油费，WETH增加10； 订单可成交量增加，Order book 增大； |
    | wrap 失败                               | wrap 10 WETH   | ETH 和WETH各有一个失败的 wrap Acitivity；ETH减少油费，WETH余额不变；订单的可成交量不变，Order book 不变； |
    | unwrap 成功余额仍然充足                 | unwrap 10 WETH | ETH 和WETH 各有一个成功的unwrap Activity；ETH 增加10 -油费，WETH减少10； |
    | unwrap 成功；余额不足，订单可成交不为零 | unwrap 10 weth | ETH 和WETH 各有一个成功的unwrap Activity；ETH 增加10 -油费，WETH减少10；Order 的可成交量减少，order book 减少 |
    | unwrap 成功，余额不足，订单可成交额为零 | unwrap 10 weth | ETH 和WETH 各有一个成功的unwrap Activity；ETH 增加10 -油费，WETH减少10；Order 被取消，order book 减少。 |
    | unwrap 失败                             | unwrap 10 WETH | ETH 和WETH各有一个失败的 unwrap Acitivity； ETH 减少 油费，WETH 余额不变； 订单的可成交量不变，Order book 不变； |

- ### ERC20 token 转账

  - ##### 场景：没有卖出或者作为支付手续费的订单

    | 分支     | 步骤            | 预期输出结果                                                 |
    | -------- | --------------- | ------------------------------------------------------------ |
    | 转账成功 | A —> B  100 LRC | A 有一个 成功的token 转出 Activity，B 有一个成功的 token转入Activity；A余额减少 100，B余额增加100 |
    | 转账失败 | A —> B  100 LRC | A 有一个 失败的token 转出 Activity，B 有一个失败的 token转入Activity；A，B的token余额都不变 |

  - ##### 场景：有卖出或者作为支付手续费的订单

    | 分支                                                         | 步骤           | 预期输出结果                                                 |
    | :----------------------------------------------------------- | -------------- | ------------------------------------------------------------ |
    | 转出成功，余额不足以让订单完全成交，但是订单可成交金额大于零 | A—>B 100 LRC   | A 有一个 成功的token 转出 Activity，B 有一个成功的 token转入Activity；A余额减少 100，B余额增加100；A的订单可成交量减少，order book减少。 |
    | 转出成功，订单可成交金额为零                                 | A —>B  100 LRC | A 有一个 成功的token 转出 Activity，B 有一个成功的 token转入Activity；A余额减少 100，B余额增加100；A的订单被取消，order book减少。 |
    | 转账失败                                                     | A —>B  100 LRC | A 有一个 失败的token 转出 Activity，B 有一个失败的 token转入Activity；A，B的token余额都不变。A，B的订单状态不受影响，order book 不受影响。 |
    | 转入成功                                                     | B—>A 100 LRC   | A 有一个 成功的token 转入 Activity，B 有一个成功的 token转出Activity；A余额增加 100，B余额减少100；A的订单可成交量增加，A因为余额不足而取消的订单没有恢复，order book增加。 |

    