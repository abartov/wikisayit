package wiki.asaf.wikisayit.data.local.settings

data class AppSettings(
    val autoUseLastProfile: Boolean = false,
    val lastUsedProfileId: Long? = null,
)
