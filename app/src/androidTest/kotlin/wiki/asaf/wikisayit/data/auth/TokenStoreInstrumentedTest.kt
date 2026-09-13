package wiki.asaf.wikisayit.data.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TokenStoreInstrumentedTest {
    @Test
    fun tokenStore_roundTripsEncryptedTokens() =
        runTest {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val testFile = File(context.cacheDir, "test_auth.preferences_pb")
            testFile.delete()
            val dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })
            val store = EncryptedDataStoreTokenStore(dataStore, AuthCryptoBox())

            assertNull(store.tokens.first())

            val tokens =
                StoredOAuthTokens(
                    accessToken = "access-123",
                    refreshToken = "refresh-456",
                    expiresAtEpochMillis = 1_800_000_000_000,
                    username = "Ijon Tichy",
                )
            store.save(tokens)

            val roundTripped = store.tokens.first()
            assertEquals(tokens, roundTripped)

            store.clear()
            assertNull(store.tokens.first())

            testFile.delete()
        }

    @Test
    fun authCryptoBox_roundTripsArbitraryBytes() {
        val box = AuthCryptoBox()
        val plaintext = "hello oauth".toByteArray(Charsets.UTF_8)

        val ciphertext = box.encrypt(plaintext)
        val decrypted = box.decrypt(ciphertext)

        assertEquals("hello oauth", String(decrypted, Charsets.UTF_8))
    }
}
