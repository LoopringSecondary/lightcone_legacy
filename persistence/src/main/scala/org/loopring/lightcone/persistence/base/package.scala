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

package org.loopring.lightcone.persistence

import slick.jdbc.MySQLProfile.api._
import scala.reflect.ClassTag
import com.google.protobuf.ByteString
import org.loopring.lightcone.proto.{TransactionRecord, TransferEvent}

package object base {

  implicit val byteStringColumnType: BaseColumnType[ByteString] =
    MappedColumnType.base[ByteString, Array[Byte]](
      bs => bs.toByteArray(),
      bytes => ByteString.copyFrom(bytes)
    )

  def enumColumnType[T <: scalapb.GeneratedEnum: ClassTag](
      enumCompanion: scalapb.GeneratedEnumCompanion[T]
    ): BaseColumnType[T] =
    MappedColumnType
      .base[T, Int](enum => enum.value, int => enumCompanion.fromValue(int))

  def eventDataColumnType(): BaseColumnType[TransactionRecord.EventData] =
    MappedColumnType
      .base[TransactionRecord.EventData, Array[Byte]](
        e => e.toByteArray,
        bytes => TransactionRecord.EventData.parseFrom(bytes)
      )

  def eventDataOptColumnType(
    ): BaseColumnType[Option[TransactionRecord.EventData]] =
    MappedColumnType
      .base[Option[TransactionRecord.EventData], Array[Byte]](
        {
          case Some(e) => e.toByteArray
          case None    => TransactionRecord.EventData().toByteArray
        },
        bytes => Some(TransactionRecord.EventData.parseFrom(bytes))
      )
}
