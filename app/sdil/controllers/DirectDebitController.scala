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

import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import sdil.actions.RegisteredAction
import sdil.config.AppConfig
import sdil.connectors.{DirectDebitBackendConnector, StartSdilReturnFromSdilFrontend}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class DirectDebitController @Inject()(
  ddConnector: DirectDebitBackendConnector,
  registeredAction: RegisteredAction,
  fcc: MessagesControllerComponents)(implicit config: AppConfig, val ec: ExecutionContext)
    extends FrontendController(fcc) {

  def startDirectDebitJourney(): Action[AnyContent] = registeredAction.async { implicit request =>
    val startRequest = StartSdilReturnFromSdilFrontend(
      returnUrl = config.sdilHomePage,
      backUrl = config.sdilHomePage
    )
    ddConnector.getSdilDirectDebitLink(startRequest).map(nextUrl => Redirect(nextUrl.nextUrl))
  }
}
