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
import org.loopring.lightcone.proto.actors.XRepeatedJobNamed

final case class Job(
    name: String,
    dalayInSeconds: Int,
    run: () ⇒ Future[Any],
    initialDalayInSeconds: Int = 0,
    delayBetweenStartAndFinish: Boolean = true,
    private[base] var sequence: Long = 0
)

trait RepeatedJobActor { actor: Actor with ActorLogging ⇒
  import context.dispatcher

  val repeatedJobs: Seq[Job]

  private val jobMap = repeatedJobs.map(j ⇒ (j.name, j)).toMap
  assert(jobMap.size == repeatedJobs.size, "job name not unique")

  repeatedJobs.foreach { job ⇒
    context.system.scheduler.scheduleOnce(
      job.initialDalayInSeconds.seconds,
      self,
      XRepeatedJobNamed(job.name)
    )
  }

  def receive: Receive = {
    case XRepeatedJobNamed(name) ⇒
      jobMap.get(name) foreach { job ⇒
        job.sequence += 1
        log.debug(s"running repeated job ${job.name}#${job.sequence}")
        val now = System.currentTimeMillis
        job.run().map { _ ⇒
          val timeTook =
            if (job.delayBetweenStartAndFinish) 0
            else (System.currentTimeMillis - now) / 1000

          val delay = Math.max(job.dalayInSeconds - timeTook, 0)
          context.system.scheduler.scheduleOnce(
            delay.seconds,
            self,
            XRepeatedJobNamed(name)
          )
        }
      }
  }
}
