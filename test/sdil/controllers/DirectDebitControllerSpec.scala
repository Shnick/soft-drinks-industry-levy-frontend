/*
 * Copyright 2023 HM Revenue & Customs
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

package sdil.controllers

import com.softwaremill.macwire._
import org.mockito.ArgumentMatchers.{eq => matching, _}
import org.mockito.Mockito._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import sdil.connectors.{DirectDebitNextUrl, StartSdilReturnFromSdilFrontend}
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DirectDebitControllerSpec extends ControllerSpec {

  "DirectDebitController" should {

    lazy val testController = wire[DirectDebitController]

    val testRedirectUrl = "/test-url"
    val testSdilEnrolment = EnrolmentIdentifier("EtmpRegistrationNumber", "XKSDIL000000033")

    "contact direct-debit-backend and get a redirect url" in {
      val testRequest = StartSdilReturnFromSdilFrontend(testConfig.sdilHomePage, testConfig.sdilHomePage)

      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn {
        Future.successful(Enrolments(Set(Enrolment("HMRC-OBTDS-ORG", Seq(testSdilEnrolment), "Active"))))
      }

      when(mockDirectDebitBackendConnector.getSdilDirectDebitLink(matching(testRequest))(any()))
        .thenReturn(Future.successful(DirectDebitNextUrl(testRedirectUrl)))

      val result = testController.startDirectDebitJourney()(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(testRedirectUrl)

      verify(mockDirectDebitBackendConnector).getSdilDirectDebitLink(matching(testRequest))(any())
    }
  }
}
