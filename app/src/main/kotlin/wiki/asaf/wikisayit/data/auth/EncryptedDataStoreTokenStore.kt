package wiki.asaf.wikisayit.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import wiki.asaf.wikisayit.di.AuthDataStore
import java.util.Base64
import javax.inject.Inject

@Serializable
private data class SerializedTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMillis: Long,
    val username: String,
)

/** [TokenStore] backed by a dedicated DataStore file, with values encrypted via [AuthCryptoBox]. */
class EncryptedDataStoreTokenStore
    @Inject
    constructor(
        @AuthDataStore private val dataStore: DataStore<Preferences>,
        private val cryptoBox: AuthCryptoBox,
    ) : TokenStore {
        override val tokens: Flow<StoredOAuthTokens?> =
            dataStore.data.map { preferences ->
                val encoded = preferences[Keys.ENCRYPTED_TOKENS] ?: return@map null
                runCatching {
                    val decrypted = cryptoBox.decrypt(Base64.getDecoder().decode(encoded))
                    Json.decodeFromString<SerializedTokens>(String(decrypted, Charsets.UTF_8))
                }.getOrNull()?.let {
                    StoredOAuthTokens(
                        accessToken = it.accessToken,
                        refreshToken = it.refreshToken,
                        expiresAtEpochMillis = it.expiresAtEpochMillis,
                        username = it.username,
                    )
                }
            }

        override suspend fun save(tokens: StoredOAuthTokens) {
            val serialized =
                Json.encodeToString(
                    SerializedTokens(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        expiresAtEpochMillis = tokens.expiresAtEpochMillis,
                        username = tokens.username,
                    ),
                )
            val encrypted = cryptoBox.encrypt(serialized.toByteArray(Charsets.UTF_8))
            dataStore.edit { it[Keys.ENCRYPTED_TOKENS] = Base64.getEncoder().encodeToString(encrypted) }
        }

        override suspend fun clear() {
            dataStore.edit { it.remove(Keys.ENCRYPTED_TOKENS) }
        }

        private object Keys {
            val ENCRYPTED_TOKENS = stringPreferencesKey("encrypted_tokens")
        }
    }
