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

package org.loopring.lightcone.actors.base

import akka.actor._
import scala.concurrent.Future
import scala.concurrent.duration._

case class Job(
    id: Int,
    name: String,
    scheduleDelay: Long,
    run: () ⇒ Future[Any]
)

class JobWithStatus(j: Job) {
  var cancel: Option[Cancellable] = None
  var lastRunTime: Long = 0
  var job: Job = j
}

trait RepeatedJobActor { actor: Actor ⇒
  import context.dispatcher

  def initAndStartNextRound(jobs: Job*): Unit = {
    jobs.foreach { job ⇒
      nextRun(new JobWithStatus(job))
    }
  }

  def nextRun(jobWithStatus: JobWithStatus) = {
    jobWithStatus.cancel.map(_.cancel())
    val delay = jobWithStatus.job.scheduleDelay -
      (System.currentTimeMillis - jobWithStatus.lastRunTime)
    if (delay > 0) {
      jobWithStatus.cancel = Some(
        context.system.scheduler.scheduleOnce(
          delay millis,
          self,
          jobWithStatus
        )
      )
    } else {
      jobWithStatus.cancel = None
      self ! jobWithStatus
    }
  }

  def receive: Receive = {
    case jobWithStatus: JobWithStatus ⇒ for {
      lastTime ← Future.successful(System.currentTimeMillis)
      _ ← jobWithStatus.job.run()
    } yield {
      jobWithStatus.lastRunTime = lastTime
      jobWithStatus.cancel = None
      nextRun(jobWithStatus)
    }
  }
}
