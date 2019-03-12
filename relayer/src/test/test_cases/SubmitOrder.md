## 提交订单

- ### 测试不同账户余额时，提交订单请求

 前置条件：新地址并且无余额无授权

<table>
  <tr>
    <th>场景</th>
    <th>步骤</th>
    <th>输入</th>
    <th>预期结果</th>
  </tr>
  <tr>
    <td>无余额无授权</td>
    <td>提交订单</td>
     <td>任意订单</td>
    <td>提交失败，无法提交订单</td>
  </tr>
  <tr>
    <td>lrc：10，授权：0</td>
     <td>提交订单</td>
    <td>sell: 8 fee:2</td>
    <td>提交成功，但是orderbook不会更改，并且该订单的可撮合金额为0，</td>
  </tr>
  <tr>
    <td>lrc：10，授权：5</td>
    <td>提交订单</td>
    <td>sell: 8 fee:2</td>
    <td>orderbook更改为4 <br> 该订单的可撮合金额为4 <br> 订单列表该订单状态为Pending <br>
    账户余额中可用金额为0
    </td>
  </tr>
    <tr>
    <td>lrc：10，授权：10</td>
    <td>提交订单</td>
    <td>sell: 8 fee:2</td>
    <td>orderbook更改为8 <br> 并且该订单的可撮合金额为8 <br> 订单列表该订单状态为Pending <br> 
    </td>
  </tr>
  <tr>
    <td rowspan="2" >lrc：15，授权：20</td>
    <td>提交第一个订单</td>
    <td>sell:8, fee:2</td>
    <td>提交成功 <br> orderbook更改为8 <br> 该订单的可撮合金额为8 <br> 订单列表改订单状态为Pending <br> 账户余额的可用金额为5</td>
  </tr>
  <tr>
    <td>提交第二个订单</td>
    <td>sell:8, fee:2</td>
    <td>提交成功 <br> orderbook更改为12 <br> 改订单的可撮合金额为4 <br> 订单列表第一个订单状态为Pending <br> 账户余额的可用金额为0 </td>
  </tr>
</table>