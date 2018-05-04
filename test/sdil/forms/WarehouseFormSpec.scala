/*
 * Copyright 2018 HM Revenue & Customs
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

package sdil.forms

import sdil.models.{Address, Sites}
import sdil.controllers.WarehouseController.form

class WarehouseFormSpec extends FormSpec {

  "The warehouse form" should {
    "bind to SecondaryWarehouses if there are no additional warehouses" in {
      val f = form.bind(Map(addWarehouse -> "false"))

      f.value mustBe Some(Sites(Nil, false, None, None))
    }

    "validate the address if there is a warehouse" in {
      mustValidateAddress(form, "additionalAddress", secondaryWarehouseData)
    }

    "bind to SecondaryWarehouses if there is a warehouse and an address is provided" in {
      val f = form.bind(secondaryWarehouseData)

      f.value mustBe Some(Sites(Nil, true, Some("name trade"), Some(Address("line 1", "line 2", "", "", "AA11 1AA"))))
    }
  }

  lazy val secondaryWarehouseData = Map(
    "addAddress" -> "true",
    "tradingName" -> "name trade",
    line1 -> "line 1",
    line2 -> "line 2",
    "additionalAddress.line3" -> "",
    "additionalAddress.line4" -> "",
    postcode -> "AA11 1AA"
  )

  lazy val addWarehouse = "addWarehouse"
  lazy val line1 = "additionalAddress.line1"
  lazy val line2 = "additionalAddress.line2"
  lazy val postcode = "additionalAddress.postcode"
}
