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

package sdil.actions

import play.api.mvc.Results._
import play.api.mvc._
import sdil.connectors.SoftDrinksIndustryLevyConnector
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.allEnrolments
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class RegisteredAction @Inject()(
  val authConnector: AuthConnector,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  mcc: MessagesControllerComponents)(implicit val executionContext: ExecutionContext)
    extends ActionRefiner[Request, RegisteredRequest] with ActionBuilder[RegisteredRequest, AnyContent]
    with AuthorisedFunctions with ActionHelpers {

  val parser: BodyParser[AnyContent] = mcc.parsers.defaultBodyParser

  override protected def refine[A](request: Request[A]): Future[Either[Result, RegisteredRequest[A]]] = {
    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    authorised(AuthProviders(GovernmentGateway)).retrieve(allEnrolments) { enrolments =>
      (getSdilEnrolment(enrolments), getUtr(enrolments)) match {
        case (Some(e), _) => Future.successful { Right(RegisteredRequest(e, request)) }
        case (None, Some(utr)) =>
          sdilConnector.retrieveSubscription(utr, "utr").map {
            case Some(subscription) =>
              Right(RegisteredRequest(EnrolmentIdentifier("sdil", subscription.sdilRef), request))
            case None =>
              Left(Redirect(sdil.controllers.routes.IdentifyController.start))
          }
        case _ => Future.successful(Left(Redirect(sdil.controllers.routes.IdentifyController.start)))
      }

    } recover {
      case _: NoActiveSession => Left(Redirect(sdil.controllers.routes.AuthenticationController.signIn()))
    }
  }
}

case class RegisteredRequest[A](
  sdilEnrolment: EnrolmentIdentifier,
  request: Request[A]
) extends WrappedRequest(request)
