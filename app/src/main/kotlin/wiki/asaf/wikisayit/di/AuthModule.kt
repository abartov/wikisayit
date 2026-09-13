package wiki.asaf.wikisayit.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import wiki.asaf.wikisayit.data.auth.AuthCryptoBox
import wiki.asaf.wikisayit.data.auth.EncryptedDataStoreTokenStore
import wiki.asaf.wikisayit.data.auth.TokenStore
import javax.inject.Qualifier
import javax.inject.Singleton

/** Distinguishes the encrypted-token-only DataStore from the plain-preferences [Context.settingsDataStore]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthDataStore

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth")

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    @AuthDataStore
    fun provideAuthDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.authDataStore

    @Provides
    @Singleton
    fun provideAuthCryptoBox(): AuthCryptoBox = AuthCryptoBox()
}

@Module
@InstallIn(SingletonComponent::class)
interface AuthBindingsModule {
    @Binds
    fun bindTokenStore(impl: EncryptedDataStoreTokenStore): TokenStore
}
