package wiki.asaf.wikisayit.data.local.settings

/** Default cap on how many entries a SPARQL query or Wikipedia category list-source can produce. */
const val DEFAULT_MAX_LIST_SIZE = 20

/** How many past categories are remembered per wiki language for the category form's dropdown. */
const val MAX_RECENT_CATEGORIES = 30

data class AppSettings(
    val autoUseLastProfile: Boolean = false,
    val lastUsedProfileId: Long? = null,
    val trimSilenceAutomatically: Boolean = true,
    val silenceThresholdSeconds: Float = 1.5f,
    /** BCP-47 tag ("en", "he", "yi"), or null to follow the system locale. */
    val interfaceLanguageTag: String? = null,
    /** Caps how many entries a SPARQL query or Wikipedia category list-source produces (s-53x). */
    val maxListSize: Int = DEFAULT_MAX_LIST_SIZE,
    /** Categories previously used to build a list, keyed by wiki language code, most recent
     * first (s-3u4) — speakers tend to record several batches from the same category. */
    val recentCategories: Map<String, List<String>> = emptyMap(),
)
