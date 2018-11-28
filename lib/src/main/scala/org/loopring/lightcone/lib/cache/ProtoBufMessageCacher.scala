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

package org.loopring.lightcone.lib.cache

import java.io._

import akka.actor.ActorSystem
import akka.util.ByteString
import javax.inject.Inject
import redis.{ ByteStringDeserializer, ByteStringSerializer, RedisCluster }

import scala.concurrent.Future

class ProtoBufMessageCacher[T <: ProtoBuf[T]] @Inject() (
    implicit
    redis: RedisCluster,
    system: ActorSystem,
    c: scalapb.GeneratedMessageCompanion[T]
) {

  implicit val ec = system.dispatcher

  private[cache] implicit val deserializer = new ProtoBufByteStringDeserializer[T]

  private[cache] implicit val serializer = new ProtoBufByteStringSerializer[T]

  private[cache] val seqAnyByteStringDeserializer = new SeqAnyByteStringDeserializer[T]

  def get(k: String): Future[Option[T]] = redis.get(k)

  def getOrElse(k: String, ttl: Option[Long] = None)(fallback: ⇒ Future[Option[T]]): Future[Option[T]] = {

    for {
      tOption ← get(k)
      tMissed = tOption.isEmpty
      fallbackOption ← if (tMissed) fallback else Future.successful(tOption)
      _ ← if (tMissed && fallbackOption.isDefined) put(k, fallbackOption.get, ttl) else Future.unit
    } yield fallbackOption

  }

  def put(k: String, v: T, ttl: Option[Long] = None): Future[Boolean] = redis.set(k, v, exSeconds = ttl)

  def putSeq(k: String, v: Seq[T], ttl: Option[Long] = None): Future[Boolean] = {
    redis.set(k, seq2ByteString(v), exSeconds = ttl)
  }

  def getSeq(k: String): Future[Option[Seq[T]]] = {
    redis.get(k)(seqAnyByteStringDeserializer)
  }

  def getSeqOrElse(k: String, ttl: Option[Long] = None)(fallback: ⇒ Future[Option[Seq[T]]]): Future[Option[Seq[T]]] = {

    for {
      tOption ← getSeq(k)
      tMissed = tOption.isEmpty
      fallbackOption ← if (tMissed) fallback else Future.successful(tOption)
      _ ← if (tMissed && fallbackOption.isDefined) putSeq(k, fallbackOption.get, ttl) else Future.unit
    } yield fallbackOption

  }

  private[cache] def seq2ByteString(data: Seq[_]): ByteString = {
    val stream: ByteArrayOutputStream = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(stream)
    oos.writeObject(data)
    oos.close
    ByteString.fromArray(stream.toByteArray)
  }

  def push(k: String, vs: Seq[T]): Future[Long] =
    redis.lpush(k, vs.map(serializer.serialize): _*)

  def pull(k: String, start: Long = 0, stop: Long = -1): Future[Seq[T]] =
    redis.lrange(k, start, stop)

}

final class SeqAnyByteStringDeserializer[T] extends ByteStringDeserializer[Seq[T]] {
  override def deserialize(bs: ByteString): Seq[T] = {
    val ois = new ObjectInputStream(new ByteArrayInputStream(bs.toArray))
    val value = ois.readObject
    ois.close

    value match {
      case vs: Seq[_] ⇒ vs.map(_.asInstanceOf[T])
      case _          ⇒ throw new Exception("can not deserializer to Seq")
    }
  }
}

final class ProtoBufByteStringDeserializer[T <: ProtoBuf[T]](
    implicit
    c: scalapb.GeneratedMessageCompanion[T]
)
  extends ByteStringDeserializer[T] {

  override def deserialize(bs: ByteString): T = c.parseFrom(bs.toArray)
}

final class ProtoBufByteStringSerializer[T <: ProtoBuf[T]] extends ByteStringSerializer[T] {

  override def serialize(data: T): ByteString = ByteString.fromArray(data.toByteArray)

}
