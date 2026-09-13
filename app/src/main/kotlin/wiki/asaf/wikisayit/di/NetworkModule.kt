package wiki.asaf.wikisayit.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import wiki.asaf.wikisayit.network.AuthTokenProvider
import wiki.asaf.wikisayit.network.NoAuthTokenProvider
import wiki.asaf.wikisayit.network.WikimediaClients
import wiki.asaf.wikisayit.network.createMediaWikiHttpClient
import javax.inject.Singleton

/**
 * Wires the shared MediaWiki networking layer ([wiki.asaf.wikisayit.network]) into the app.
 *
 * [AuthTokenProvider] is bound to [NoAuthTokenProvider] until the authentication epic supplies a
 * real OAuth-backed implementation; swapping that binding is all callers of [WikimediaClients]
 * will need once it lands.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = createMediaWikiHttpClient()

    @Provides
    @Singleton
    fun provideAuthTokenProvider(): AuthTokenProvider = NoAuthTokenProvider

    @Provides
    @Singleton
    fun provideWikimediaClients(
        httpClient: HttpClient,
        authTokenProvider: AuthTokenProvider,
    ): WikimediaClients = WikimediaClients(httpClient, authTokenProvider)
}
