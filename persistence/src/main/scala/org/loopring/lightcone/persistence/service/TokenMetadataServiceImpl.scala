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

package org.loopring.lightcone.persistence.service

import com.google.inject.Inject
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.persistence.dals._
import org.loopring.lightcone.proto.{ErrorCode, TokenMetadata}
import scala.concurrent.{ExecutionContext, Future}

class TokenMetadataServiceImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    tokenMetadataDal: TokenMetadataDal)
    extends TokenMetadataService {

  def saveToken(tokenMetadata: TokenMetadata): Future[ErrorCode] =
    tokenMetadataDal.saveToken(tokenMetadata)

  def saveTokens(tokenMetadatas: Seq[TokenMetadata]): Future[Seq[String]] =
    tokenMetadataDal.saveTokens(tokenMetadatas)

  def updateToken(tokenMetadata: TokenMetadata): Future[ErrorCode] =
    tokenMetadataDal.updateToken(tokenMetadata)

  def getTokens(
      reloadFromDatabase: Boolean = false
    ): Future[Seq[TokenMetadata]] =
    tokenMetadataDal.getTokens(reloadFromDatabase)

  def getTokens(tokens: Seq[String]): Future[Seq[TokenMetadata]] =
    tokenMetadataDal.getTokens(tokens)

  def updateBurnRate(
      token: String,
      burnDate: Double
    ): Future[ErrorCode] =
    tokenMetadataDal.updateBurnRate(token, burnDate)

  def disableToken(address: Address): Future[ErrorCode] =
    tokenMetadataDal.disableToken(address)
}
