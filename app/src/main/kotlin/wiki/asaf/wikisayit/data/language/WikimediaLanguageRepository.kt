package wiki.asaf.wikisayit.data.language

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import wiki.asaf.wikisayit.network.WikimediaClients
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val CACHE_FILE_NAME = "wikimedia_languages.json"
private const val CACHE_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000

/** MediaWiki's message-documentation / debugging pseudo-languages: not languages anyone speaks. */
private val PSEUDO_LANGUAGE_CODES = setOf("qqq", "qqx")

private val languageInfoJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class LanguageInfoResponse(val query: LanguageInfoQuery? = null)

@Serializable
internal data class LanguageInfoQuery(val languageinfo: Map<String, LanguageInfoEntry> = emptyMap())

@Serializable
internal data class LanguageInfoEntry(
    val code: String = "",
    val autonym: String = "",
    val name: String = "",
)

/**
 * Turns a `meta=languageinfo` response (fetched with `uselang=en`, so `name` is the English name)
 * into picker options. A language CLDR can name in the phone's locale is shown that way, as the
 * Locale-based picker always did; every other language — which is the whole point of using the
 * Wikimedia list — is shown by its English name.
 */
internal fun parseLanguageInfo(
    response: LanguageInfoResponse,
    localizedName: (String) -> String? = ::cldrDisplayName,
): List<LanguageCatalog.LanguageOption> =
    response.query?.languageinfo.orEmpty().values
        .filter { it.code.isNotBlank() && it.name.isNotBlank() && it.code !in PSEUDO_LANGUAGE_CODES }
        .map { entry ->
            val displayName = localizedName(entry.code) ?: entry.name
            LanguageCatalog.LanguageOption(
                isoCode = entry.code,
                displayName = displayName.replaceFirstChar { it.uppercase() },
                englishName = entry.name,
                autonym = entry.autonym,
            )
        }

private fun cldrDisplayName(code: String): String? {
    val name = Locale(code).getDisplayLanguage(Locale.getDefault())
    return name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
}

/**
 * Keeps [LanguageCatalog] stocked with every language Wikimedia supports, from the MediaWiki
 * API's own `meta=languageinfo` list (which includes languages with no two-letter ISO 639-1
 * code, such as Dagbani). The last good response is cached on disk so the picker is complete
 * offline and on later launches; until either source answers, [LanguageCatalog] serves its
 * built-in Locale-based fallback.
 */
@Singleton
class WikimediaLanguageRepository
    @Inject
    constructor(
        private val wikimediaClients: WikimediaClients,
        @ApplicationContext private val context: Context,
    ) {
        /** Loads the cached list, then refreshes it from the network if the cache is missing or stale. Never throws. */
        suspend fun refresh() {
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            val cacheAge = readCache(cacheFile)
            if (cacheAge != null && cacheAge < CACHE_MAX_AGE_MILLIS) return
            fetchAndCache(cacheFile)
        }

        /** Installs the cached list if there is a usable one; returns its age in millis, or null. */
        private suspend fun readCache(cacheFile: File): Long? =
            withContext(Dispatchers.IO) {
                try {
                    if (!cacheFile.exists()) return@withContext null
                    val options = parseLanguageInfo(languageInfoJson.decodeFromString(cacheFile.readText()))
                    if (options.isEmpty()) return@withContext null
                    LanguageCatalog.install(options)
                    System.currentTimeMillis() - cacheFile.lastModified()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }

        private suspend fun fetchAndCache(cacheFile: File) {
            try {
                val response =
                    wikimediaClients.commons.getAction<LanguageInfoResponse>(
                        mapOf(
                            "action" to "query",
                            "meta" to "languageinfo",
                            "liprop" to "code|autonym|name",
                            "uselang" to "en",
                        ),
                    )
                val options = parseLanguageInfo(response)
                if (options.isEmpty()) return
                LanguageCatalog.install(options)
                withContext(Dispatchers.IO) {
                    cacheFile.writeText(languageInfoJson.encodeToString(LanguageInfoResponse.serializer(), response))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Offline or API trouble: keep whatever list we already have (cache or Locale fallback).
            }
        }
    }
