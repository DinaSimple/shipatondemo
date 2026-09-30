// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.location

/** A place/address suggestion from the search provider (cafe, street, park…). */
data class PlaceSuggestion(
    val name: String,
    val subtitle: String?,       // "Poblenou, 16 street, Barcelona"
    val point: GeoPoint,
    val area: AreaLabel,
    val category: String? = null, // e.g. "amenity:cafe"
)

/** Autocomplete / place directory — provider-agnostic (Photon today, replaceable). */
interface PlaceSearchProvider {
    suspend fun search(query: String, near: GeoPoint?, limit: Int = 8): List<PlaceSuggestion>
}

/** Coordinates → city + postal code. */
interface ReverseGeocoder {
    suspend fun areaAt(point: GeoPoint): AreaLabel?
    /** Best human label for a map-picked point (street/place). */
    suspend fun placeAt(point: GeoPoint): PlaceSuggestion? = null
}

/** Search-as-you-type rules, independent of provider. */
object PlaceSearchPolicy {
    const val MIN_QUERY_CHARS = 2
    const val DEBOUNCE_MS = 300L
    const val PROMPT = "Giveaway pickup point"

    fun normalize(q: String): String = q.trim().replace(Regex("\\s+"), " ")

    fun shouldSearch(q: String): Boolean = normalize(q).length >= MIN_QUERY_CHARS

    /**
     * Initial-letter matching first: names starting with the query, then names with a word
     * starting with it, then the rest (provider order kept within each group).
     */
    fun rank(query: String, results: List<PlaceSuggestion>): List<PlaceSuggestion> {
        val q = fold(normalize(query))
        if (q.isEmpty()) return results
        fun score(s: PlaceSuggestion): Int {
            val n = fold(s.name)
            return when {
                n.startsWith(q) -> 0
                n.split(' ', '-', ',').any { it.startsWith(q) } -> 1
                else -> 2
            }
        }
        return results.withIndex().sortedWith(compareBy({ score(it.value) }, { it.index })).map { it.value }
    }

    /** Range of the name to render bold (design highlights the typed prefix). */
    fun highlight(name: String, query: String): IntRange? {
        val q = fold(normalize(query))
        if (q.isEmpty()) return null
        val idx = fold(name).indexOf(q)
        return if (idx < 0) null else idx until idx + q.length
    }

    /** Lowercase + strip common Latin diacritics, length-preserving ("Plaça" → "placa"). */
    fun fold(s: String): String = buildString(s.length) {
        for (c in s.lowercase()) append(DIACRITICS[c] ?: c)
    }

    private val DIACRITICS: Map<Char, Char> = buildMap {
        "àáâãäåā".forEach { put(it, 'a') }; "èéêëē".forEach { put(it, 'e') }
        "ìíîïī".forEach { put(it, 'i') }; "òóôõöøō".forEach { put(it, 'o') }
        "ùúûüū".forEach { put(it, 'u') }; put('ñ', 'n'); put('ç', 'c'); put('ÿ', 'y'); put('ý', 'y')
        put('·', '.')
    }
}
