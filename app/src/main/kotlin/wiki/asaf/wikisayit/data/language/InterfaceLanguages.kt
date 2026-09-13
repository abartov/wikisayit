package wiki.asaf.wikisayit.data.language

import wiki.asaf.wikisayit.BuildConfig
import java.text.Collator
import java.util.Locale

/**
 * Interface languages offered in Settings. The set of BCP-47 tags is computed at build time
 * from which `values-<lang>` resource directories exist (see the `android {}` block in
 * `app/build.gradle.kts`) — not hardcoded — so a translation landing from Translatewiki.net
 * becomes selectable with nothing but a rebuild.
 */
object InterfaceLanguages {
    data class Option(val tag: String, val displayName: String)

    /** Sorted by each language's own name for itself (its endonym), e.g. "עברית" before "English". */
    val options: List<Option> by lazy {
        val collator = Collator.getInstance()
        BuildConfig.SUPPORTED_INTERFACE_LANGUAGES
            .split(",")
            .filter { it.isNotBlank() }
            .map { tag ->
                val locale = Locale.forLanguageTag(tag)
                Option(tag = tag, displayName = locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) })
            }
            .sortedWith(compareBy(collator) { it.displayName })
    }
}
