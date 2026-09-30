// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.data.geo

import app.freetotake.domain.location.AreaLabel
import app.freetotake.domain.location.GeoPoint
import app.freetotake.domain.location.PlaceSearchPolicy
import app.freetotake.domain.location.PlaceSearchProvider
import app.freetotake.domain.location.PlaceSuggestion
import app.freetotake.domain.location.ReverseGeocoder
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Photon (OpenStreetMap-based, komoot) — free, no key, search-as-you-type, POIs, location bias.
 * Public instance is fair-use without SLA: fine for MVP; switch [baseUrl] to a self-hosted
 * instance (or swap the provider behind [PlaceSearchProvider]) when traffic grows.
 */
class PhotonGeocoder(
    private val http: HttpClient,
    private val baseUrl: String = "https://photon.komoot.io",
    private val userAgent: String = "FreeToTake-Android/1.2",
) : PlaceSearchProvider, ReverseGeocoder {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String, near: GeoPoint?, limit: Int): List<PlaceSuggestion> {
        if (!PlaceSearchPolicy.shouldSearch(query)) return emptyList()
        val body = http.get("$baseUrl/api/") {
            header("User-Agent", userAgent)
            parameter("q", PlaceSearchPolicy.normalize(query))
            parameter("limit", limit)
            near?.let { parameter("lat", it.lat); parameter("lon", it.lng) } // location bias
        }.bodyAsText()
        return PlaceSearchPolicy.rank(query, parse(body))
    }

    override suspend fun areaAt(point: GeoPoint): AreaLabel? = placeAt(point)?.area

    override suspend fun placeAt(point: GeoPoint): PlaceSuggestion? {
        val body = http.get("$baseUrl/reverse") {
            header("User-Agent", userAgent)
            parameter("lat", point.lat)
            parameter("lon", point.lng)
            parameter("limit", 1)
        }.bodyAsText()
        return parse(body).firstOrNull()?.copy(point = point)
    }

    internal fun parse(body: String): List<PlaceSuggestion> =
        json.parseToJsonElement(body).jsonObject["features"]?.jsonArray.orEmpty().mapNotNull { f ->
            val o = f.jsonObject
            val p = o["properties"]?.jsonObject ?: return@mapNotNull null
            val c = o["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return@mapNotNull null
            fun s(k: String) = p.str(k)
            val street = listOfNotNull(s("street"), s("housenumber")).joinToString(" ").ifEmpty { null }
            val city = s("city") ?: s("town") ?: s("village") ?: s("county")
            val name = s("name") ?: street ?: return@mapNotNull null
            PlaceSuggestion(
                name = name,
                subtitle = listOfNotNull(s("district"), street?.takeIf { it != name }, city).distinct().joinToString(", ").ifEmpty { null },
                point = GeoPoint(lat = c[1].jsonPrimitive.double, lng = c[0].jsonPrimitive.double),
                area = AreaLabel(city, s("postcode")),
                category = listOfNotNull(s("osm_key"), s("osm_value")).joinToString(":").ifEmpty { null },
            )
        }

    private fun JsonObject.str(k: String): String? = this[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
}
