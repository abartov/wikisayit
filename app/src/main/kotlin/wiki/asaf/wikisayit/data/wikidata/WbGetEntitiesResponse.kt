package wiki.asaf.wikisayit.data.wikidata

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Response shape of `action=wbgetentities` (only the fields the existence check needs). */
@Serializable
data class WbGetEntitiesResponse(
    val entities: Map<String, WbEntity> = emptyMap(),
)

@Serializable
data class WbEntity(
    /** Non-null (empty string) when [id] doesn't correspond to a real entity. */
    val missing: String? = null,
    /** Keyed by property id (e.g. "P443"); presence of the key is all the checker needs. */
    val claims: Map<String, JsonElement> = emptyMap(),
    /** Only populated for lexemes. */
    val forms: List<WbForm> = emptyList(),
    /** Only populated for items, when requested via `props=labels`. */
    val labels: Map<String, WbRepresentation> = emptyMap(),
    /** Only populated for lexemes, when requested via `props=lemmas`. */
    val lemmas: Map<String, WbRepresentation> = emptyMap(),
)

@Serializable
data class WbForm(
    val id: String,
    val representations: Map<String, WbRepresentation> = emptyMap(),
    val claims: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class WbRepresentation(
    val language: String,
    val value: String,
)

/** Preferred-language lookup shared by every place that resolves a Wikidata label/lemma/form
 * representation: the requested language, else whatever's available, else [fallback]. */
fun Map<String, WbRepresentation>.labelFor(
    isoCode: String,
    fallback: String,
): String = this[isoCode]?.value ?: values.firstOrNull()?.value ?: fallback
