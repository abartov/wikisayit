package wiki.asaf.wikisayit.data.local.settings

/** Default cap on how many entries a SPARQL query or Wikipedia category list-source can produce. */
const val DEFAULT_MAX_LIST_SIZE = 20

data class AppSettings(
    val autoUseLastProfile: Boolean = false,
    val lastUsedProfileId: Long? = null,
    val trimSilenceAutomatically: Boolean = true,
    val silenceThresholdSeconds: Float = 1.5f,
    /** BCP-47 tag ("en", "he", "yi"), or null to follow the system locale. */
    val interfaceLanguageTag: String? = null,
    /** Caps how many entries a SPARQL query or Wikipedia category list-source produces (s-53x). */
    val maxListSize: Int = DEFAULT_MAX_LIST_SIZE,
)
