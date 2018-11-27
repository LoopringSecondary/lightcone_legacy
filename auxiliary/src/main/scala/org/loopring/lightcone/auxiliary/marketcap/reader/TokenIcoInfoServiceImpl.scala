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

package org.loopring.lightcone.auxiliary.marketcap.reader

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.slick.scaladsl.SlickSession
import com.google.inject.Inject
import org.loopring.lightcone.proto.auxiliary._

class TokenIcoInfoServiceImpl @Inject() (
    implicit
    system: ActorSystem,
    mat: ActorMaterializer,
    session: SlickSession
) extends DatabaseAccessor
  with TokenIcoInfoService {

  import session.profile.api._
  import system.dispatcher

  private implicit lazy val saveXTokenIcoInfo =
    (info: XTokenIcoInfo) ⇒ sqlu"""
    INSERT INTO t_token_ico_info (
      token_address,
      ico_start_date,
      ico_end_date,
      hard_cap,
      soft_cap,
      token_raised,
      ico_price,
      from_country
    ) VALUES (
      ${info.tokenAddress},
      ${info.icoStartDate},
      ${info.icoEndDate},
      ${info.hardCap},
      ${info.softCap},
      ${info.tokenRaised},
      ${info.icoPrice},
      ${info.country}
    ) ON DUPLICATE KEY UPDATE
      ico_start_date=${info.icoStartDate},
      ico_end_date=${info.icoEndDate},
      hard_cap=${info.hardCap},
      soft_cap=${info.softCap},
      token_raised=${info.tokenRaised},
      ico_price=${info.icoPrice},
      from_country=${info.country}
    """

  private implicit lazy val toGetXTokenIcoInfo =
    (r: ResultRow) ⇒ XTokenIcoInfo(
      tokenAddress = r <<,
      icoStartDate = r <<,
      icoEndDate = r <<,
      hardCap = r <<,
      softCap = r <<,
      tokenRaised = r <<,
      icoPrice = r <<,
      country = r <<
    )

  def saveOrUpdate(iocInfo: XTokenIcoInfo) = saveOrUpdate(iocInfo)

  override def queryXTokenIcoInfo(tokenAddress: Option[String]) = {
    sql"""
    SELECT
      token_address,
      ico_start_date,
      ico_end_date,
      hard_cap,
      soft_cap,
      token_raised,
      ico_price,
      from_country
    FROM t_token_ico_info
    WHERE token_address like concat('%', ${tokenAddress.getOrElse("")}, '%')
    """
      .list[XTokenIcoInfo]
      .map(XGetTokenIcoInfoRes(_))
  }

  override def queryAllXTokenIcoInfo() = {
    sql"""
    SELECT
      token_address,
      ico_start_date,
      ico_end_date,
      hard_cap,
      soft_cap,
      token_raised,
      ico_price,
      from_country
    FROM t_token_ico_info
    """
      .list[XTokenIcoInfo]
      .map(XGetTokenIcoInfoRes(_))
  }

}
