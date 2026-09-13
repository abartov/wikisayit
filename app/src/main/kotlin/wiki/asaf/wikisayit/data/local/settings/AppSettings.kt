package wiki.asaf.wikisayit.data.local.settings

data class AppSettings(
    val autoUseLastProfile: Boolean = false,
    val lastUsedProfileId: Long? = null,
    val trimSilenceAutomatically: Boolean = true,
    val silenceThresholdSeconds: Float = 1.5f,
    /** BCP-47 tag ("en", "he", "yi"), or null to follow the system locale. */
    val interfaceLanguageTag: String? = null,
)
