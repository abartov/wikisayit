package wiki.asaf.wikisayit.data.language

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Language names/codes for the profile language picker.
 *
 * Starts out with the CLDR data Android ships via [Locale] — two-letter ISO 639-1 codes only —
 * and is replaced by the full list of languages Wikimedia supports (including three-letter-only
 * ones like Dagbani, `dag`) as soon as [WikimediaLanguageRepository] has one, from the network
 * or its on-disk cache. The Locale list remains the offline, first-run fallback.
 */
object LanguageCatalog {
    /**
     * [displayName] is what the picker shows and what gets stored in the profile (localized to the
     * phone's language where CLDR knows the language, else [englishName]). [englishName] is for
     * on-wiki text; [autonym] is the language's name for itself, searchable but not required.
     */
    data class LanguageOption(
        val isoCode: String,
        val displayName: String,
        val englishName: String = displayName,
        val autonym: String = "",
    )

    private val localeFallback: List<LanguageOption> by lazy {
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

    private val _languages = MutableStateFlow<List<LanguageOption>?>(null)

    /** Emits the current list; changes once when the Wikimedia list arrives so open pickers refresh. */
    val languages: StateFlow<List<LanguageOption>?> = _languages.asStateFlow()

    val all: List<LanguageOption> get() = _languages.value ?: localeFallback

    /** Replaces the fallback list with the Wikimedia one. An empty list is ignored. */
    fun install(options: List<LanguageOption>) {
        if (options.isEmpty()) return
        _languages.value = options.distinctBy { it.isoCode }.sortedBy { it.displayName.lowercase() }
    }

    internal fun resetForTest() {
        _languages.value = null
    }

    /** The language's English name (e.g. "Hebrew" for "he"), for on-wiki text that shouldn't be
     * written in whatever locale the phone happens to be set to. Falls back to [isoCode] itself
     * when neither Wikimedia nor CLDR has an English name for it. */
    fun englishName(isoCode: String): String {
        val code = isoCode.trim()
        if (code.isEmpty()) return ""
        _languages.value?.firstOrNull { it.isoCode.equals(code, ignoreCase = true) }?.let { return it.englishName }
        val name = Locale(code).getDisplayLanguage(Locale.ENGLISH)
        return if (name.isBlank() || name.equals(code, ignoreCase = true)) code else name
    }

    fun search(
        query: String,
        limit: Int = 30,
    ): List<LanguageOption> = search(all, query, limit)

    internal fun search(
        options: List<LanguageOption>,
        query: String,
        limit: Int = 30,
    ): List<LanguageOption> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return options.take(limit)
        val needle = trimmed.lowercase(Locale.getDefault())
        val startsWith = mutableListOf<LanguageOption>()
        val contains = mutableListOf<LanguageOption>()
        for (option in options) {
            val names =
                listOf(option.displayName, option.englishName, option.autonym)
                    .filter { it.isNotBlank() }
                    .map { it.lowercase(Locale.getDefault()) }
            when {
                names.any { it.startsWith(needle) } || option.isoCode.equals(needle, ignoreCase = true) ->
                    startsWith += option
                names.any { it.contains(needle) } -> contains += option
            }
        }
        return (startsWith + contains).take(limit)
    }
}
