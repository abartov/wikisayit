package wiki.asaf.wikisayit.network

/**
 * A MediaWiki-based wiki that WikiSayIt talks to via the MediaWiki REST API
 * (and, for Wikidata, the Wikibase REST API mounted under the same host).
 */
enum class MediaWikiSite(val restApiBaseUrl: String) {
    COMMONS(restApiBaseUrl = "https://commons.wikimedia.org/w/rest.php/"),
    WIKIDATA(restApiBaseUrl = "https://www.wikidata.org/w/rest.php/"),
}
