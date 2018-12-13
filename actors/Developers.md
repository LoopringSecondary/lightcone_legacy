


### 关于错误消息返回

我们之前是在每个消息的返回值里面加入错误码，后续不在这样做 -- 一旦出错，actor直接返回XError结构。

请求的Actor因此需要等待返回值的时候，这样处理：

val res: Future[...] = (req ? RequestActor) match { 
  case resp: SomeResp =>
  case err: XError    =>
}