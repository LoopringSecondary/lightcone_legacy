#测试


## 测试环境说明

当前测试环境外部依赖的Mysql和Geth，都通过docker提供，保证测试环境的一致和稳定。
1、Mysql会在每次测试时，删除并重建所有表，保持干净的数据库环境。
2、Geth中目前包含Loopring合约2.0以及三种代币，accounts(0)地址上有充足的eth和代币的金额、授权。

## EthereumSupport说明
为了便于测试和设置金额等，添加了如下函数
1、transferEth 进行eth转账
2、transferErc20 进行token转账
3、approveErc20 进行token授权
4、getUniqueAccountWithoutEth 
如果在充足金额下即可测试，并不需要重新设置，可以使用accounts(0)
如果需要一个干净的地址，该方法会获取一个测试期间唯一的地址(**该地址并没有ETH**)，避免各个测试之间的相互影响，获取地址之后，可以通过transfer方法进行相应的金额、授权设置
但是如果从accounts(0)中转了大量的金额的话，测试完毕，为了不影响其他的测试，还是需要归还的

## OrderGenerateSupport说明
生成一个订单，默认的owner是accounts(0)

## HttpSupport 说明

singleRequest 发起一个jsonrpc请求，如果返回的是ErrorException的话，该处会抛出异常
expectOrderbookRes 是个阻塞的方法会一直等待直到获取的orderbook满足条件，或者抛出超时异常

## support.scala
放置测试的全局变量，尤其是mysqlContainer、ethContainer和addressGenerator


## 待解决问题
目前每个测试都需要重新启动Akka系统，而且为了等待系统稳定，等待了5s，导致跑完整个测试耗时较长



