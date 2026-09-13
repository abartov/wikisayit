package wiki.asaf.wikisayit.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setAutoUseLastProfile(enabled: Boolean)

    suspend fun setLastUsedProfileId(profileId: Long?)
}

class DataStoreSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        private object Keys {
            val AUTO_USE_LAST_PROFILE = booleanPreferencesKey("auto_use_last_profile")
            val LAST_USED_PROFILE_ID = longPreferencesKey("last_used_profile_id")
        }

        override val settings: Flow<AppSettings> =
            dataStore.data.map { preferences ->
                AppSettings(
                    autoUseLastProfile = preferences[Keys.AUTO_USE_LAST_PROFILE] ?: false,
                    lastUsedProfileId = preferences[Keys.LAST_USED_PROFILE_ID],
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
    }
