/*
 * Copyright 2022 HM Revenue & Customs
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

package sdil.config

import play.api.Configuration
import play.api.libs.json.JsResultException
import sdil.models.{RegistrationFormData, ReturnsFormData, SdilReturn}
import uk.gov.hmrc.crypto.CompositeSymmetricCrypto
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.{CacheMap, ShortLivedCache, ShortLivedHttpCaching}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import sdil.connectors._

import scala.concurrent.{ExecutionContext, Future}

class ReturnsFormDataCache(
  val configuration: Configuration,
  val shortLiveCache: ShortLivedHttpCaching
)(implicit val crypto: CompositeSymmetricCrypto, ec: ExecutionContext)
    extends ServicesConfig(configuration) with ShortLivedCache {

  def cache(internalId: String, body: ReturnsFormData)(implicit hc: HeaderCarrier): Future[CacheMap] =
    cache(s"$internalId-sdil-completed-return", "retFormData", body)

  def get(internalId: String)(implicit hc: HeaderCarrier): Future[Option[ReturnsFormData]] =
    fetchAndGetEntry[ReturnsFormData](s"$internalId-sdil-completed-return", "retFormData").recover {
      case _: JsResultException => None
    }

  def clear(internalId: String)(implicit hc: HeaderCarrier): Future[Unit] =
    remove(s"$internalId-sdil-completed-return") map { _ =>
      ()
    }

  def clearInternalIdOnly(internalId: String)(implicit hc: HeaderCarrier): Future[Unit] =
    remove(internalId) map { _ =>
      ()
    }

  def clearBySdilNumber(sdilNo: String)(implicit hc: HeaderCarrier): Future[Unit] =
    remove(sdilNo) map { _ =>
      ()
    }

}
