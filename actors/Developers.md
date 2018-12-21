


### 关于错误消息返回

我们之前是在每个消息的返回值里面加入错误码，后续不在这样做 -- 一旦出错，actor直接返回XError结构。

请求的Actor因此需要等待返回值的时候，这样处理：

```scala
val res: Future[...] = (req ? RequestActor).mapAs[MyExpectedType]

```


如果 `(req ? RequestActor)`返回的是其他类型的值，那么有个ErrorException类型的异常会被抛出来，为了处理这个异常，
请用 safefuture里面的 forwardTo 或者 sendTo， 比如：

```scala
def receive = LoggingReceive {
    case x: MyReq = (for{
        res <- (anotherActor ? x).mapAs[MyResp]
        _ = if (res.something) throw ErrorException(XErrorCode.SOME_ERROR, "msg")
    } yield res) forwardTo nextActor
    // } yield res) sendTo nextActor
}
```

在使用forwardTo的时候，for构建的future会被forward到nextActor，但如果处理过程出错了(包括`anotherActor`返回了一个XError，或者下一行throw了)，会有个ErrorException被返回给原先的sender。 sendTo和forwardTo不同的是，内部用了forward，而不是tell。



### 关于recovery

MarketManagerActor 和 MultiAccountManagerActor 在启动后，如果配置中指定需要recover订单，那么这个两个类型的Actor启动后，就会切换到`recover`模式，并且各自向一个叫 OrderRecoverCoordinator的singleton actor发送一个`XRecover.Request`类型的消息。OrderRecoverCoordinator的singleton在接收到`XRecover.Request`消息后并不后立刻启动订单的恢复逻辑，而是等待30秒（可配置），如果在这30秒内，任何其它的MarketManagerActor 和 MultiAccountManagerActor 实例也发来同样的消息，那么就会把过去时间积累的`XRecover.Request`消息放到一个`XRecover.Batch`中，再等30秒。如果30面内没有新的请求，那么这个Batch会被发到给一个被均匀分片的OrderRecoverActor中的一个。OrderRecoverCoordinator 保证每个Batch有个唯一的递增的ID，而且每个OrderRecoverActor被动态生成处理对应的Batch。OrderRecoverActor在处理完自己特定的batch后就会stop自己。

OrderRecoverActor在接到一个Batch后会立即把这个batch发回给OrderRecoverCoordinator，好让OrderRecoverCoordinator知道自己的actorRef，这样如果中途batch中的一个请求取消了，OrderRecoverCoordinator可以通过这个actorRef来通知这个OrderRecoverActor。

之后OrderRecoverActor会不断给自己发XRecover.RetrieveOrders消息，出发不断读取数据库里面的订单，然后把这些订单**逐一发给MultiAccountManagerActor**。如果再也读不到订单，这时候就结束恢复，并且通知OrderRecoverCoordinatorActor这个batch已经处理完成，同时通知这个Batch里面每个recover的MarketManagerActor 和 MultiAccountManagerActor recover已经完成。

