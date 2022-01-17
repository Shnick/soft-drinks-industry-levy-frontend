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

package ltbs.play.scaffold

import java.time.LocalDate

import cats.implicits._
import enumeratum._
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.{Constraint, Constraints, Invalid, Valid}
import play.api.i18n.Messages
import play.api.libs.functional.syntax.{unlift, _}
import play.api.libs.json._
import play.twirl.api.Html
import sdil.controllers.ShowBackLink
import sdil.forms.FormHelpers
import sdil.models._
import sdil.models.backend.{Site, UkAddress}
import views.html.uniform

import scala.util.Try

object SdilComponents extends FormHelpers {

  implicit val showBackLink: ShowBackLink = ShowBackLink(true)

  implicit val longTupleFormatter: Format[(Long, Long)] = (
    (JsPath \ "lower").format[Long] and
      (JsPath \ "higher").format[Long]
  )((a: Long, b: Long) => (a, b), unlift({ x: (Long, Long) =>
    Tuple2.unapply(x)
  }))

  lazy val contactDetailsMapping: Mapping[ContactDetails] = mapping(
    "fullName" -> text.verifying(Constraint { x: String =>
      x match {
        case ""                       => Invalid("error.fullName.required")
        case name if name.length > 40 => Invalid("error.fullName.over")
        case name if !name.matches("^[a-zA-Z &`\\-\\'\\.^]{1,40}$") =>
          Invalid("error.fullName.invalid")
        case _ => Valid
      }
    }),
    "position" -> text.verifying(Constraint { x: String =>
      x match {
        case ""                                => Invalid("error.position.required")
        case position if position.length > 155 => Invalid("error.position.over")
        case position if !position.matches("^[a-zA-Z &`\\-\\'\\.^]{1,155}$") =>
          Invalid("error.position.invalid")
        case _ => Valid
      }
    }),
    "phoneNumber" -> text.verifying(Constraint { x: String =>
      x match {
        case ""                         => Invalid("error.phoneNumber.required")
        case phone if phone.length > 24 => Invalid("error.phoneNumber.over")
        case phone if !phone.matches("^[A-Z0-9 )/(\\-*#+]{1,24}$") =>
          Invalid("error.phoneNumber.invalid")
        case _ => Valid
      }
    }),
    "email" -> text.verifying(
      combine(
        Constraint { x: String =>
          x match {
            case ""                   => Invalid("error.email.required")
            case e if e.length >= 132 => Invalid("error.email.over")
            case _                    => Valid
          }
        },
        Constraints.emailAddress
      ))
  )(ContactDetails.apply)(ContactDetails.unapply)

  lazy val startDate: Mapping[LocalDate] = tuple(
    "day"   -> text,
    "month" -> text,
    "year"  -> text
  ).verifying(
      "error.date.emptyfields",
      x =>
        x match {
          case (d: String, m: String, y: String) if (trim(d) == "" && trim(m) == "" && trim(y) == "") => false
          case _                                                                                      => true
      })
    .verifying(
      "error.start-date.missing",
      x =>
        x match {
          case (d: String, m: String, y: String) if (trim(d) == "" && trim(m) != "" && trim(y) != "") => false
          case _                                                                                      => true
      })
    .verifying(
      "error.start-month.missing",
      x =>
        x match {
          case (d: String, m: String, y: String) if (trim(d) != "" && trim(m) == "" && trim(y) != "") => false
          case _                                                                                      => true
      })
    .verifying(
      "error.start-year.missing",
      x =>
        x match {
          case (d: String, m: String, y: String) if (trim(d) != "" && trim(m) != "" && trim(y) == "") => false
          case _                                                                                      => true
      })
    .verifying(
      "error.start-day-and-month.missing",
      x =>
        x match {
          case (d: String, m: String, y: String) if (trim(d) == "" && trim(m) == "" && trim(y) != "") => false
          case _                                                                                      => true
      })
    .verifying(
      "error.start-month-and-year.missing",
      x =>
        x match {
          case (d: String, m: String, y: String) if (trim(d) != "" && trim(m) == "" && trim(y) == "") => false
          case _                                                                                      => true
      })
    .verifying(
      "error.start-day-and-year.missing",
      x =>
        x match {
          case (d: String, m: String, y: String) if (trim(d) == "" && trim(m) != "" && trim(y) == "") => false
          case _                                                                                      => true
      })
    .verifying(
      "error.date.invalid",
      x =>
        x match {
          case (d: String, m: String, y: String) if (trim(d) != "" && trim(m) != "" && trim(y) != "") =>
            Try(LocalDate.of(trim(y).toInt, trim(m).toInt, trim(d).toInt)).isSuccess
          case _ => true
      }
    )
    .transform(
      { case (d, m, y) => LocalDate.of(trim(y).toInt, trim(m).toInt, trim(d).toInt) },
      duy => (duy.getDayOfMonth.toString, duy.getMonthValue.toString, duy.getYear.toString)
    )

  lazy val cancelRegDate: Mapping[LocalDate] = tuple(
    "day"   -> text,
    "month" -> text,
    "year"  -> text
  ).verifying(
      "error.cancel-registration-date.emptyfields",
      x =>
        x match {
          case (d: String, m: String, y: String) if (trim(d) == "" && trim(m) == "" && trim(y) == "") => false
          case _                                                                                      => true
      })
    .verifying(
      "error.cancel-registration-date-day.missing",
      x =>
        x match {
          case (d: String, m: String, y: String) if (trim(d) == "" && trim(m) != "" && trim(y) != "") => false
          case _                                                                                      => true
      })
    .verifying(
      "error.cancel-registration-date-month.missing",
      x =>
        x match {
          case (d: String, m: String, y: String) if (trim(d) != "" && trim(m) == "" && trim(y) != "") => false
          case _                                                                                      => true
      })
    .verifying(
      "error.cancel-registration-date-year.missing",
      x =>
        x match {
          case (d: String, m: String, y: String) if (trim(d) != "" && trim(m) != "" && trim(y) == "") => false
          case _                                                                                      => true
      })
    .verifying(
      "error.cancel-registration-date-day-and-month.missing",
      x =>
        x match {
          case (d: String, m: String, y: String) if (trim(d) == "" && trim(m) == "" && trim(y) != "") => false
          case _                                                                                      => true
      }
    )
    .verifying(
      "error.cancel-registration-date-month-and-year.missing",
      x =>
        x match {
          case (d: String, m: String, y: String) if (trim(d) != "" && trim(m) == "" && trim(y) == "") => false
          case _                                                                                      => true
      }
    )
    .verifying(
      "error.cancel-registration-date-day-and-year.missing",
      x =>
        x match {
          case (d: String, m: String, y: String) if (trim(d) == "" && trim(m) != "" && trim(y) == "") => false
          case _                                                                                      => true
      }
    )
    .verifying(
      "error.date.invalid",
      x =>
        x match {
          case (d: String, m: String, y: String) if (trim(d) != "" && trim(m) != "" && trim(y) != "") =>
            Try(LocalDate.of(trim(y).toInt, trim(m).toInt, trim(d).toInt)).isSuccess
          case _ => true
      }
    )
    .transform(
      { case (d, m, y) => LocalDate.of(trim(y).toInt, trim(m).toInt, trim(d).toInt) },
      duy => (duy.getDayOfMonth.toString, duy.getMonthValue.toString, duy.getYear.toString)
    )

  def trim(inputStr: String) = inputStr.trim()

  def numeric(key: String): Mapping[Int] =
    text
      .verifying(s"error.$key.required", _.nonEmpty)
      .verifying(s"error.$key.number", v => v.isEmpty || Try(v.toInt).isSuccess)
      .transform[Int](_.toInt, _.toString)

  sealed trait OrganisationType extends EnumEntry
  object OrganisationType extends Enum[OrganisationType] {
    val values = findValues
    case object limitedCompany extends OrganisationType
    case object limitedLiabilityPartnership extends OrganisationType
    case object partnership extends OrganisationType
    case object soleTrader extends OrganisationType
    case object unincorporatedBody extends OrganisationType
  }

  sealed trait OrganisationTypeSoleless extends EnumEntry
  object OrganisationTypeSoleless extends Enum[OrganisationTypeSoleless] {
    val values = findValues
    case object limitedCompany extends OrganisationTypeSoleless
    case object limitedLiabilityPartnership extends OrganisationTypeSoleless
    case object partnership extends OrganisationTypeSoleless
    case object unincorporatedBody extends OrganisationTypeSoleless
  }

  sealed trait ProducerType extends EnumEntry
  object ProducerType extends Enum[ProducerType] {
    val values = findValues
    case object Large extends ProducerType
    case object Small extends ProducerType
    case object Not extends ProducerType
  }

  lazy val warehouseSiteMapping: Mapping[Site] = mapping(
    "address"     -> ukAddressMapping,
    "tradingName" -> optional(tradingNameMapping)
  ) { (a, b) =>
    Site.apply(a, none, b, none)
  }(Site.unapply(_).map {
    case (address, _, tradingName, _) =>
      (address, tradingName)
  })

  lazy val tradingNameMapping: Mapping[String] = {
    text.transform[String](_.trim, identity).verifying(optionalTradingNameConstraint)
  }

  private def optionalTradingNameConstraint: Constraint[String] = Constraint {
    case s if s.length > 160                          => Invalid("error.tradingName.length")
    case s if !s.matches("""^[a-zA-Z0-9 '.&\\/]*$""") => Invalid("error.tradingName.invalid")
    case _                                            => Valid
  }

  lazy val packagingSiteMapping: Mapping[Site] = mapping(
    "address" -> ukAddressMapping
  ) { a =>
    Site.apply(a, none, none, none)
  }(Site.unapply(_).map(x => x._1))

  private val ukAddressMapping: Mapping[UkAddress] =
    addressMapping.transform(UkAddress.fromAddress, Address.fromUkAddress)

  lazy val addressMapping: Mapping[Address] = mapping(
    "line1"    -> mandatoryAddressLine("line1"),
    "line2"    -> mandatoryAddressLine("line2"),
    "line3"    -> optionalAddressLine("line3"),
    "line4"    -> optionalAddressLine("line4"),
    "postcode" -> postcode
  )(Address.apply)(Address.unapply)

  private def mandatoryAddressLine(key: String): Mapping[String] =
    text.transform[String](_.trim, s => s).verifying(combine(required(key), optionalAddressLineConstraint(key)))

  private def optionalAddressLine(key: String): Mapping[String] =
    text.transform[String](_.trim, s => s).verifying(optionalAddressLineConstraint(key))

  private def optionalAddressLineConstraint(key: String): Constraint[String] = Constraint {
    case a if !a.matches("""^[A-Za-z0-9 \-,.&'\/]*$""") => Invalid(s"error.$key.invalid")
    case b if b.length > 35                             => Invalid(s"error.$key.over")
    case _                                              => Valid
  }

  def postcode: Mapping[String] = {
    val postcodeRegex = "^[A-Z]{1,2}[0-9][0-9A-Z]?\\s?[0-9][A-Z]{2}$|BFPO\\s?[0-9]{1,5}$"
    val specialRegex = """^[A-Za-z0-9 _]*[A-Za-z0-9][A-Za-z0-9 _]*$"""

    text
      .transform[String](_.toUpperCase.trim, identity)
      .verifying(Constraint { x: String =>
        x match {
          case ""                               => Invalid("error.postcode.empty")
          case pc if !pc.matches(specialRegex)  => Invalid("error.postcode.special")
          case pc if !pc.matches(postcodeRegex) => Invalid("error.postcode.invalid")
          case _                                => Valid
        }
      })
  }

  def longTupToLitreage(in: (Long, Long)): Option[Litreage] =
    if (in.isEmpty) None else Litreage(in._1, in._2).some

}
