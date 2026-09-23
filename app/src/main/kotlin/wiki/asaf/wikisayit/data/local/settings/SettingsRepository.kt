package wiki.asaf.wikisayit.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setAutoUseLastProfile(enabled: Boolean)

    suspend fun setLastUsedProfileId(profileId: Long?)

    suspend fun setTrimSilenceAutomatically(enabled: Boolean)

    suspend fun setSilenceThresholdSeconds(seconds: Float)

    suspend fun setInterfaceLanguageTag(tag: String?)

    suspend fun setMaxListSize(size: Int)

    /** Moves [category] to the top of [languageCode]'s past categories, adding it if new. */
    suspend fun rememberCategory(
        languageCode: String,
        category: String,
    )
}

class DataStoreSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        private object Keys {
            val AUTO_USE_LAST_PROFILE = booleanPreferencesKey("auto_use_last_profile")
            val LAST_USED_PROFILE_ID = longPreferencesKey("last_used_profile_id")
            val TRIM_SILENCE_AUTOMATICALLY = booleanPreferencesKey("trim_silence_automatically")
            val SILENCE_THRESHOLD_SECONDS = floatPreferencesKey("silence_threshold_seconds")
            val INTERFACE_LANGUAGE_TAG = stringPreferencesKey("interface_language_tag")
            val MAX_LIST_SIZE = intPreferencesKey("max_list_size")
            val RECENT_CATEGORIES = stringPreferencesKey("recent_categories")
        }

        override val settings: Flow<AppSettings> =
            dataStore.data.map { preferences ->
                AppSettings(
                    autoUseLastProfile = preferences[Keys.AUTO_USE_LAST_PROFILE] ?: false,
                    lastUsedProfileId = preferences[Keys.LAST_USED_PROFILE_ID],
                    trimSilenceAutomatically = preferences[Keys.TRIM_SILENCE_AUTOMATICALLY] ?: true,
                    silenceThresholdSeconds = preferences[Keys.SILENCE_THRESHOLD_SECONDS] ?: 1.5f,
                    interfaceLanguageTag = preferences[Keys.INTERFACE_LANGUAGE_TAG],
                    maxListSize = preferences[Keys.MAX_LIST_SIZE] ?: DEFAULT_MAX_LIST_SIZE,
                    recentCategories = decodeRecentCategories(preferences[Keys.RECENT_CATEGORIES]),
                )
            }

        override suspend fun setAutoUseLastProfile(enabled: Boolean) {
            dataStore.edit { it[Keys.AUTO_USE_LAST_PROFILE] = enabled }
        }

        override suspend fun setLastUsedProfileId(profileId: Long?) {
            dataStore.edit {
                if (profileId == null) {
                    it.remove(Keys.LAST_USED_PROFILE_ID)
                } else {
                    it[Keys.LAST_USED_PROFILE_ID] = profileId
                }
            }
        }

        override suspend fun setTrimSilenceAutomatically(enabled: Boolean) {
            dataStore.edit { it[Keys.TRIM_SILENCE_AUTOMATICALLY] = enabled }
        }

        override suspend fun setSilenceThresholdSeconds(seconds: Float) {
            dataStore.edit { it[Keys.SILENCE_THRESHOLD_SECONDS] = seconds }
        }

        override suspend fun setInterfaceLanguageTag(tag: String?) {
            dataStore.edit {
                if (tag == null) {
                    it.remove(Keys.INTERFACE_LANGUAGE_TAG)
                } else {
                    it[Keys.INTERFACE_LANGUAGE_TAG] = tag
                }
            }
        }

        override suspend fun setMaxListSize(size: Int) {
            dataStore.edit { it[Keys.MAX_LIST_SIZE] = size.coerceAtLeast(1) }
        }

        override suspend fun rememberCategory(
            languageCode: String,
            category: String,
        ) {
            val name = category.trim().replace(Regex("\\s+"), " ")
            if (name.isEmpty()) return
            dataStore.edit {
                val all = decodeRecentCategories(it[Keys.RECENT_CATEGORIES])
                val updated =
                    (listOf(name) + all[languageCode].orEmpty().filterNot { old -> old == name })
                        .take(MAX_RECENT_CATEGORIES)
                it[Keys.RECENT_CATEGORIES] = encodeRecentCategories(all + (languageCode to updated))
            }
        }
    }

/** One "lang<TAB>category" pair per line; neither a language code nor a category title can
 * contain a tab or a newline, so no escaping is needed. */
private fun encodeRecentCategories(categories: Map<String, List<String>>): String =
    categories
        .flatMap { (language, names) -> names.map { "$language\t$it" } }
        .joinToString("\n")

private fun decodeRecentCategories(encoded: String?): Map<String, List<String>> =
    encoded
        .orEmpty()
        .lineSequence()
        .mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0 || tab == line.lastIndex) null else line.substring(0, tab) to line.substring(tab + 1)
        }.groupBy({ it.first }, { it.second })
