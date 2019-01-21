/*
 * Copyright 2018 Loopring Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.loopring.lightcone.persistence.dals

import org.loopring.lightcone.proto._
import scala.concurrent.Await
import scala.concurrent.duration._

class OrderCutoffJobDalSpec extends DalSpec[OrderCutoffJobDal] {
  def getDal = new OrderCutoffJobDalImpl()

  "orderCutoffJob spec" must "save job successfully" in {
    info("save a job")
    val now = timeProvider.getTimeMillis
    val job = OrderCutoffJob(
      broker = "0x1",
      owner = "0x2",
      tradingPair = "0x3",
      cutoff = timeProvider.getTimeSeconds(),
      createAt = now
    )
    val r1 = dal.saveJob(job)
    val res1 = Await.result(r1.mapTo[Boolean], 5 second)
    assert(res1)
    val q1 = dal.getJobs()
    val q11 = Await.result(q1.mapTo[Seq[OrderCutoffJob]], 5 second)
    assert(q11.length == 1)

    info("save a different broker")
    val r2 = dal.saveJob(job.copy(broker = "0x11"))
    val res2 = Await.result(r2.mapTo[Boolean], 5 second)
    assert(res2)
    val q2 = dal.getJobs()
    val q22 = Await.result(q2.mapTo[Seq[OrderCutoffJob]], 5 second)
    assert(q22.length == 2)

    info("save a different owner")
    val r3 = dal.saveJob(job.copy(owner = "0x22"))
    val res3 = Await.result(r3.mapTo[Boolean], 5 second)
    assert(res3)
    val q3 = dal.getJobs()
    val q33 = Await.result(q3.mapTo[Seq[OrderCutoffJob]], 5 second)
    assert(q33.length == 3)

    info("save a different trading pair")
    val r4 = dal.saveJob(job.copy(tradingPair = "0x33"))
    val res4 = Await.result(r4.mapTo[Boolean], 5 second)
    assert(res4)
    val q4 = dal.getJobs()
    val q44 = Await.result(q4.mapTo[Seq[OrderCutoffJob]], 5 second)
    assert(q44.length == 4)

    info("save a duplicate cutoff job, will only update record")
    val r5 = dal.saveJob(job.copy(createAt = now + 1))
    val res5 = Await.result(r5.mapTo[Boolean], 5 second)
    assert(res5)
    val q5 = dal.getJobs()
    val q55 = Await.result(q5.mapTo[Seq[OrderCutoffJob]], 5 second)
    assert(q55.length == 4)

    info("delete a job")
    val d1 = dal.deleteJob(job)
    val d11 = Await.result(d1.mapTo[Boolean], 5 second)
    assert(d11)
    val q6 = dal.getJobs()
    val q66 = Await.result(q6.mapTo[Seq[OrderCutoffJob]], 5 second)
    assert(q66.length == 3)
  }
}
