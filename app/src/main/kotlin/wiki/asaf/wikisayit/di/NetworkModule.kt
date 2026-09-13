package wiki.asaf.wikisayit.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import wiki.asaf.wikisayit.network.AuthTokenProvider
import wiki.asaf.wikisayit.network.WikimediaClients
import wiki.asaf.wikisayit.network.createMediaWikiHttpClient
import wiki.asaf.wikisayit.network.oauth.OAuthAuthTokenProvider
import javax.inject.Singleton

/** Wires the shared MediaWiki networking layer ([wiki.asaf.wikisayit.network]) into the app. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = createMediaWikiHttpClient()

    @Provides
    @Singleton
    fun provideWikimediaClients(
        httpClient: HttpClient,
        authTokenProvider: AuthTokenProvider,
    ): WikimediaClients = WikimediaClients(httpClient, authTokenProvider)
}

@Module
@InstallIn(SingletonComponent::class)
interface NetworkBindingsModule {
    @Binds
    fun bindAuthTokenProvider(impl: OAuthAuthTokenProvider): AuthTokenProvider
}
