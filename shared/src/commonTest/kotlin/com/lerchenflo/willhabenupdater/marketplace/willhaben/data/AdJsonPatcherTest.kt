package com.lerchenflo.willhabenupdater.marketplace.willhaben.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.lerchenflo.willhabenupdater.datasource.network.AppJson
import com.lerchenflo.willhabenupdater.listings.domain.Condition
import com.lerchenflo.willhabenupdater.listings.domain.ListingContent
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.DeliveryOptions
import com.lerchenflo.willhabenupdater.marketplace.willhaben.domain.WillhabenListingFields
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

class AdJsonPatcherTest {

    private val serverJson = AppJson.parseToJsonElement(
        """
        {"heading":"Alt","description":"Alt<br />Text","price":200.0,"postCode":"6850","locationId":117,
         "phoneNo":"keep-me","productId":67,"categoryTreeId":3653,
         "treeAttributes":[
           {"attributeTreeId":21,"attributeNode":{"label":"Zustand","code":"Zustand"},"selectedValueTreeIds":[23],
            "selectedAttributeValueNodes":[{"label":"Gebraucht","code":"gebraucht"}]},
           {"attributeTreeId":2535,"attributeNode":{"label":"Übergabe","code":"Uebergabe"},"selectedValueTreeIds":[2536],
            "selectedAttributeValueNodes":[{"label":"Selbstabholung","code":"PICKUP"}]},
           {"attributeTreeId":77,"attributeNode":{"label":"Marke","code":"BRAND"},"selectedValueTreeIds":[78],
            "selectedAttributeValueNodes":[{"label":"Husqvarna","code":"HUSQ"}]}
         ],
         "contextLinkList":{"contextLink":[
           {"id":"presave","serviceName":"iad","relativePath":"/bap/presave"},
           {"id":"imageList","serviceName":"iad","relativePath":"/atimage/42"}
         ]}}
        """
    ).jsonObject

    private val content = ListingContent(
        title = "Rasentraktor Kart",
        description = "Gut & günstig\nAbholung in Dornbirn",
        priceCents = 18050,
        condition = Condition.LIKE_NEW,
        postcode = "6900",
    )
    private val fields = WillhabenListingFields(
        attributes = mapOf("BRAND" to listOf("STIGA")),
        locationId = 200,
        delivery = DeliveryOptions(pickup = true, shipping = true),
    )

    private fun patched(): JsonObject = AdJsonPatcher.patch(serverJson, content, fields)

    private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.attributeCodes(code: String): List<String>? =
        this["treeAttributes"]!!.jsonArray.map { it.jsonObject }
            .firstOrNull { it["attributeNode"]!!.jsonObject["code"]!!.jsonPrimitive.content == code }
            ?.get("selectedAttributeValueNodes")!!.jsonArray.map { it.jsonObject["code"]!!.jsonPrimitive.content }

    @Test
    fun textFieldsAndPriceAreWritten() {
        val json = patched()

        assertThat(json.text("heading")).isEqualTo("Rasentraktor Kart")
        assertThat(json.text("description")).isEqualTo("Gut &amp; günstig<br />Abholung in Dornbirn")
        assertThat((json["price"] as JsonPrimitive).content).isEqualTo("180.5")
        assertThat(json.text("postCode")).isEqualTo("6900")
        assertThat(json.text("locationId")).isEqualTo("200")
    }

    @Test
    fun conditionHandoverAndAttributesAreWrittenAsCodes() {
        val json = patched()

        assertThat(json.attributeCodes("Zustand")).isEqualTo(listOf("MintCondition"))
        assertThat(json.attributeCodes("Uebergabe")).isEqualTo(listOf("PICKUP", "Versand"))
        assertThat(json.attributeCodes("BRAND")).isEqualTo(listOf("STIGA"))
    }

    @Test
    fun fieldsTheAppDoesNotManageAreKept() {
        val json = patched()

        assertThat(json.text("phoneNo")).isEqualTo("keep-me")
        assertThat(json.text("categoryTreeId")).isEqualTo("3653")
        assertThat((json["contextLinkList"] as JsonObject)["contextLink"]).isEqualTo(serverJson["contextLinkList"]!!.jsonObject["contextLink"])
    }

    @Test
    fun missingPriceIsWrittenAsNull() {
        val json = AdJsonPatcher.patch(serverJson, content.copy(priceCents = null), fields)

        assertThat(json.text("price")).isNull()
    }

    @Test
    fun otherVerticalsOnlyGetKeysTheyAlreadyHave() {
        // Auto & Motor JSON is not recorded yet: never add marketplace keys or attributes to it
        val motorJson = AppJson.parseToJsonElement(
            """{"heading":"Alt","price":200.0,"vehicleMileage":12000,"locationId":117,
               "treeAttributes":[{"attributeNode":{"code":"Zustand"},"selectedAttributeValueNodes":[{"code":"gebraucht"}]}]}"""
        ).jsonObject

        val json = AdJsonPatcher.patch(motorJson, content, fields.copy(adTypeId = 21))

        assertThat(json.text("heading")).isEqualTo("Rasentraktor Kart")
        assertThat((json["price"] as JsonPrimitive).content).isEqualTo("180.5")
        assertThat(json.text("vehicleMileage")).isEqualTo("12000")
        // The location goes with the postcode, so it is written when the JSON has it
        assertThat(json.text("locationId")).isEqualTo("200")
        assertThat(json["treeAttributes"]).isEqualTo(motorJson["treeAttributes"])
        assertThat(json.keys).isEqualTo(motorJson.keys)
    }

    @Test
    fun contextLinkIsFoundById() {
        assertThat(ContextLinks.find(serverJson, "imageList")).isEqualTo(ContextLink("iad", "/atimage/42"))
        assertThat(ContextLinks.find(serverJson, "selfLinkFinish")).isNull()
    }

    @Test
    fun linkRouteJoinsServiceAndPath() {
        assertThat(ContextLink("iad", "/bap/presave").route).isEqualTo("/iad/bap/presave")
    }

}
