package wiki.asaf.wikisayit.network

import io.ktor.client.HttpClient

/** The pair of [MediaWikiClient]s WikiSayIt talks to, sharing one underlying [HttpClient]. */
class WikimediaClients(
    httpClient: HttpClient,
    authTokenProvider: AuthTokenProvider = NoAuthTokenProvider,
) {
    val commons = MediaWikiClient(MediaWikiSite.COMMONS, httpClient, authTokenProvider)
    val wikidata = MediaWikiClient(MediaWikiSite.WIKIDATA, httpClient, authTokenProvider)
}
