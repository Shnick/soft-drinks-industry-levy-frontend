/*
 * Copyright 2021 HM Revenue & Customs
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

import ltbs.uniform._
import common.web._
import interpreters.playframework._
import play.api.i18n.MessagesApi
import play.api.mvc.{AnyContent, Request}
import play.twirl.api.Html
import views.html.uniform
import sdil.config.AppConfig
import cats.implicits._
import sdil.uniform.SdilComponentsNew
import scala.concurrent.duration.Duration

trait HmrcPlayInterpreter extends PlayInterpreter[Html] with SdilComponentsNew with InferWebAsk[Html] {

  val config: AppConfig
  def messagesApi: MessagesApi
  def ufViews: views.uniform.Uniform

  lazy private val futureAdapter = FutureAdapter.rerunOnStateChange[Html](Duration.Inf)
  implicit protected def futAdapt[A: Codec] = futureAdapter.apply[A]

  implicit def messages(
    implicit request: Request[AnyContent]
  ): UniformMessages[Html] = {
    messagesApi.preferred(request).convertMessages() |+|
      UniformMessages.bestGuess
  }.map { Html(_) }

  def pageChrome(
    key: List[String],
    errors: ErrorTree,
    html: Option[Html],
    breadcrumbs: List[String],
    request: Request[AnyContent],
    messages: UniformMessages[Html]
  ): Html = {
    import play.filters.csrf._
    import play.filters.csrf.CSRF.Token
    val Token(_, csrf: String) = CSRF.getToken(request).get
    ufViews.base(key, errors, html, breadcrumbs, csrf)(messages, request, config)
  }

  def renderAnd[T](
    pageIn: PageIn[Html],
    stepDetails: StepDetails[Html, T],
    members: Seq[(String, Html)]
  ): Html = members.toList match {
    case (_, sole) :: Nil => sole
    case many =>
      views.html.softdrinksindustrylevy.helpers.surround(
        stepDetails.fieldKey,
        stepDetails.tell,
        stepDetails.errors,
        pageIn.messages
      )(many.map(_._2): _*)
  }

  /** allows using progressive reveal for options, eithers or other sealed trait hierarchies
    *  */
  def renderOr[T](
    pageIn: PageIn[Html],
    stepDetails: StepDetails[Html, T],
    alternatives: Seq[(String, Option[Html])],
    selected: Option[String]
  ): Html = {

    // in some cases we might want options to appear in a sequence other than their natural order,
    // alternatively we could implement an implicit `WebAsk[Html, Option[A]]` (for example)
    // but this reduces the amount of logic needed
    val reordered = alternatives.map(_._1).toList match {
      case "None" :: "Some" :: Nil => alternatives.reverse
      case _                       => alternatives
    }

    views.html.softdrinksindustrylevy.helpers.radios(
      stepDetails.fieldKey,
      stepDetails.tell,
      reordered.map(_._1),
      selected,
      stepDetails.errors,
      pageIn.messages,
      reordered.collect { case (k, Some(v)) => (k, v) }.toMap
    )
  }

}
