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

package sdil.controllers

import scala.language.higherKinds

import ltbs.uniform._, validation.Rule
import sdil.models._, backend._, retrieved._
import sdil.connectors.SoftDrinksIndustryLevyConnector
import cats.implicits._
import izumi.reflect.Tag
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import play.api.mvc._
import play.api.i18n._
import sdil.config.AppConfig
import scala.concurrent.Future
import sdil.actions.RegisteredAction
import play.api.Logger
import scala.concurrent.ExecutionContext
import sdil.uniform.SaveForLaterPersistenceNew
import sdil.config.RegistrationFormDataCache
import ltbs.uniform.interpreters.playframework.PersistenceEngine
import sdil.actions.AuthorisedRequest
import sdil.actions.RegisteredRequest
import play.twirl.api.Html
import java.time._
import java.time.format._
import sdil.utility.stringToFormatter
import views.html.uniform

class ReturnsControllerNew(
  mcc: MessagesControllerComponents,
  val config: AppConfig,
  val ufViews: views.uniform.Uniform,
  registeredAction: RegisteredAction,
  sdilConnector: SoftDrinksIndustryLevyConnector,
  cache: RegistrationFormDataCache
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc) with I18nSupport with HmrcPlayInterpreter {

  val logger: Logger = Logger(this.getClass())
  override def defaultBackLink = "/soft-drinks-industry-levy"

  def index(year: Int, quarter: Int, nilReturn: Boolean, id: String): Action[AnyContent] = registeredAction.async {
    implicit request =>
      implicit lazy val persistence =
        SaveForLaterPersistenceNew[RegisteredRequest[AnyContent]](_.sdilEnrolment.value)(
          s"returns-$year-$quarter",
          cache.shortLiveCache)

      import CheckYourAnswers._

      val sdilRef = request.sdilEnrolment.value
      val period = ReturnPeriod(year, quarter)

      (for {
        subscription   <- sdilConnector.retrieveSubscription(sdilRef).map { _.get }
        pendingReturns <- sdilConnector.returns_pending(subscription.utr)
        broughtForward <- if (config.balanceAllEnabled)
                           sdilConnector.balanceHistory(sdilRef, withAssessment = false).map { x =>
                             extractTotal(listItemsWithTotal(x))
                           } else sdilConnector.balance(sdilRef, withAssessment = false)
        isSmallProd <- sdilConnector.checkSmallProducerStatus(sdilRef, period).flatMap {
                        case Some(x) =>
                          x // the given sdilRef matches a customer that was a small producer at some point in the quarter
                        case None => false
                      }

        r <- if (pendingReturns.contains(period)) {
              def submitReturn(sdilReturn: SdilReturn): Future[Unit] =
                sdilConnector.returns_update(subscription.utr, period, sdilReturn)
              interpret(
                ReturnsControllerNew
                  .journey(sdilRef, period, None, subscription, broughtForward, isSmallProd, submitReturn))
                .run(id, config = journeyConfig) { _ =>
                  Redirect(routes.ServicePageController.show())
                }
            } else
              Redirect(routes.ServicePageController.show()).pure[Future]
      } yield r) recoverWith {
        case t: Throwable => {
          logger.error(s"Exception occurred while retrieving pendingReturns for sdilRef =  $sdilRef", t)
          Redirect(routes.ServicePageController.show()).pure[Future]
        }
      }
  }
}

object ReturnsControllerNew {

  private[controllers] def journey(
    sdilRef: String,
    period: ReturnPeriod,
    default: Option[SdilReturn] = None,
    subscription: RetrievedSubscription,
    broughtForward: BigDecimal,
    isSmallProd: Boolean,
    submitReturn: SdilReturn => Future[Unit]
  ) = {

    val costLower = BigDecimal("0.18")
    val costHigher = BigDecimal("0.24")

    def calculateSubtotal(d: List[(String, (Long, Long), Int)]): BigDecimal =
      d.map { case (_, (l, h), m) => costLower * l * m + costHigher * h * m }.sum

    def returnAmount(sdilReturn: SdilReturn, isSmallProducer: Boolean): List[(String, (Long, Long), Int)] = {
      val ra = List(
        ("packaged-as-a-contract-packer", sdilReturn.packLarge, 1),
        ("exemptions-for-small-producers", sdilReturn.packSmall.map { _.litreage }.combineAll, 0),
        ("brought-into-uk", sdilReturn.importLarge, 1),
        ("brought-into-uk-from-small-producers", sdilReturn.importSmall, 0),
        ("claim-credits-for-exports", sdilReturn.export, -1),
        ("claim-credits-for-lost-damaged", sdilReturn.wastage, -1)
      )
      if (!isSmallProducer)
        ("own-brands-packaged-at-own-sites", sdilReturn.ownBrand, 1) :: ra
      else
        ra
    }

    def taxEstimation(r: SdilReturn): BigDecimal = {
      val t = r.packLarge |+| r.importLarge |+| r.ownBrand
      (t._1 * costLower |+| t._2 * costHigher) * 4
    }

    for {
      ownBrands <- askEmptyOption[(Long, Long)](
                    "own-brands-packaged-at-own-sites",
                    default = default.map { _.packLarge }
                  ) emptyUnless !subscription.activity.smallProducer
      contractPacked <- askEmptyOption[(Long, Long)](
                         "packaged-as-a-contract-packer"
                       ) emptyUnless !subscription.activity.smallProducer
      //TODO validation for SmallProducer
      smallProds <- askListSimple[SmallProducer](
                     "small-producer-details",
                     "add-small-producer",
                     validation = Rule.nonEmpty[List[SmallProducer]],
                     default = default.map { _.packSmall }
                   ) emptyUnless ask[Boolean]("exemptions-for-small-producers", default = default.map {
                     _.packSmall.nonEmpty
                   })
      imports <- askEmptyOption[(Long, Long)]("brought-into-uk", default.map { _.importLarge })
      importsSmall <- askEmptyOption[(Long, Long)]("brought-into-uk-from-small-producers", default.map {
                       _.importSmall
                     })
      exportCredits <- askEmptyOption[(Long, Long)]("claim-credits-for-exports", default.map { _.export })
      wastage       <- askEmptyOption[(Long, Long)]("claim-credits-for-lost-damaged", default.map { _.wastage })
      sdilReturn = SdilReturn(ownBrands, contractPacked, smallProds, imports, importsSmall, exportCredits, wastage)
      isNewImporter = !sdilReturn.totalImported.isEmpty && !subscription.activity.importer
      isNewPacker = !sdilReturn.totalPacked.isEmpty && !subscription.activity.contractPacker
      inner = uniform.fragments.return_variation_continue(isNewImporter, isNewPacker)(_: Messages)
      _ <- tell("return-change-registration", inner) when isNewImporter || isNewPacker
      newPackingSites <- (
                          for {
                            firstPackingSite <- interact[Boolean](
                                                 "pack-at-business-address-in-return",
                                                 Address.fromUkAddress(subscription.address)) flatMap {
                                                 case true =>
                                                   pure(Address.fromUkAddress(subscription.address))
                                                 case false => ask[Address]("first-production-site")
                                               }
                            //TODO: validation for Address
                            packingSites <- askListSimple[Address](
                                             "production-site-details",
                                             "site-in-return",
                                             default = Some(firstPackingSite :: Nil),
                                             validation = Rule.nonEmpty[List[Address]]
                                           ).map(_.map(Site.fromAddress))
                          } yield packingSites
                        ) when isNewPacker && subscription.productionSites.isEmpty
      newWarehouses <- (for {
                        addWarehouses <- ask[Boolean]("ask-secondary-warehouses-in-return")
                        //TODO: validation for Warehouse
                        warehouses <- askListSimple[Warehouse](
                                       "secondary-warehouse-details",
                                       "warehouse-in-return",
                                       validation = Rule.nonEmpty[List[Warehouse]]
                                     ) map (_.map(Site.fromWarehouse)) emptyUnless addWarehouses
                      } yield warehouses) when isNewImporter //&& subscription.warehouseSites.isEmpty
      variation = ReturnsVariation(
        orgName = subscription.orgName,
        ppobAddress = subscription.address,
        importer = (isNewImporter, (sdilReturn.totalImported).combineN(4)),
        packer = (isNewPacker, (sdilReturn.totalPacked).combineN(4)),
        warehouses = newWarehouses.getOrElse(List.empty[Site]),
        packingSites = newPackingSites.getOrElse(List.empty[Site]),
        phoneNumber = subscription.contact.phoneNumber,
        email = subscription.contact.email,
        taxEstimation = taxEstimation(sdilReturn)
      )
      data = returnAmount(sdilReturn, isSmallProd)
      subtotal = calculateSubtotal(data)
      total = subtotal - broughtForward
      _ <- tell(
            "check-your-answers",
            uniform.fragments.returnsCYA(
              key = "check-your-answers",
              lineItems = data,
              costLower = costLower,
              costHigher = costHigher,
              subtotal = subtotal,
              broughtForward = broughtForward,
              total = total,
              variation = variation.some,
              subscription = subscription,
              originalReturn = None
            )(_: Messages)
          )
      _ <- convertWithKey(s"return-submission")(submitReturn(sdilReturn)) // sdilConnector.returns_update(subscription.utr, period, sdilReturn))
      _ <- nonReturn("return-complete")
      _ <- end(
            "return-sent", { msg: Messages =>
              val now = LocalDate.now
              val returnDate = msg(
                "return-sent.returnsDoneMessage",
                period.start.format("MMMM"),
                period.end.format("MMMM"),
                period.start.getYear.toString,
                subscription.orgName,
                LocalTime.now(ZoneId.of("Europe/London")).format(DateTimeFormatter.ofPattern("h:mma")).toLowerCase,
                now.format("dd MMMM yyyy")
              )

              val formatTotal =
                if (total < 0)
                  f"-£${total.abs}%,.2f"
                else
                  f"£$total%,.2f"

              val prettyPeriod =
                msg(
                  s"period.check-your-answers",
                  period.start.format("MMMM"),
                  period.end.format("MMMM yyyy")
                )

              val getTotal =
                if (total <= 0)
                  msg("return-sent.subheading.nil-return")
                else {
                  msg(
                    "return-sent.subheading",
                    prettyPeriod,
                    subscription.orgName
                  )
                }

              val whatHappensNext = views.html.uniform.fragments
                .returnsPaymentsBlurb(
                  subscription = subscription,
                  paymentDate = period,
                  sdilRef = sdilRef,
                  total = total,
                  formattedTotal = formatTotal,
                  variation = variation,
                  lineItems = data,
                  costLower = costLower,
                  costHigher = costHigher,
                  subtotal = subtotal,
                  broughtForward = broughtForward
                )(msg)
                .some

              views.html.uniform
                .journeyEndNew("return-sent", now, Html(returnDate).some, whatHappensNext, Html(getTotal).some)(msg)
            }
          )
    } yield (sdilReturn, variation)
  }
}
