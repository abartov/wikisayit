package wiki.asaf.wikisayit.data.language

import java.util.Locale

/** Language names/codes for the profile language picker, from the CLDR data Android ships via [Locale]. */
object LanguageCatalog {
    data class LanguageOption(val isoCode: String, val displayName: String)

    val all: List<LanguageOption> by lazy {
        Locale.getISOLanguages()
            .mapNotNull { code ->
                val name = Locale(code).getDisplayLanguage(Locale.getDefault())
                if (name.isBlank() || name == code) {
                    null
                } else {
                    LanguageOption(isoCode = code, displayName = name.replaceFirstChar { it.uppercase() })
                }
            }
            .distinctBy { it.isoCode }
            .sortedBy { it.displayName }
    }

    fun search(
        query: String,
        limit: Int = 30,
    ): List<LanguageOption> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return all.take(limit)
        val needle = trimmed.lowercase(Locale.getDefault())
        val startsWith = mutableListOf<LanguageOption>()
        val contains = mutableListOf<LanguageOption>()
        for (option in all) {
            val haystack = option.displayName.lowercase(Locale.getDefault())
            when {
                haystack.startsWith(needle) -> startsWith += option
                haystack.contains(needle) -> contains += option
            }
        }
        return (startsWith + contains).take(limit)
    }
}
