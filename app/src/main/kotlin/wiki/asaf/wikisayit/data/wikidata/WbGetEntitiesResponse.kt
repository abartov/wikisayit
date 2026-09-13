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
