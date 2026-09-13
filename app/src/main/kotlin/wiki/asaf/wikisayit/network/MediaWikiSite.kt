package wiki.asaf.wikisayit.network

/**
 * A MediaWiki-based wiki that WikiSayIt talks to via the MediaWiki REST API
 * (and, for Wikidata, the Wikibase REST API mounted under the same host).
 */
enum class MediaWikiSite(val restApiBaseUrl: String, val actionApiBaseUrl: String) {
    COMMONS(
        restApiBaseUrl = "https://commons.wikimedia.org/w/rest.php/",
        actionApiBaseUrl = "https://commons.wikimedia.org/w/api.php",
    ),
    WIKIDATA(
        restApiBaseUrl = "https://www.wikidata.org/w/rest.php/",
        actionApiBaseUrl = "https://www.wikidata.org/w/api.php",
    ),
}
