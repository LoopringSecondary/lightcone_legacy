


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

#### MarketManagerActor的Recovery

#### MultiAccountManagerActor 和 AccountManagerActor的Recovery