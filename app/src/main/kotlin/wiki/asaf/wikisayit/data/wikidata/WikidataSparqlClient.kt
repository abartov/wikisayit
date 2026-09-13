package wiki.asaf.wikisayit.data.wikidata

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import wiki.asaf.wikisayit.ui.session.EntryKind
import javax.inject.Inject

private const val SPARQL_ENDPOINT = "https://query.wikidata.org/sparql"
private const val ENTITY_URI_PREFIX = "http://www.wikidata.org/entity/"

private val sparqlJson = Json { ignoreUnknownKeys = true }

/** One `?item`/`?lexeme` binding from a SPARQL result row, resolved to a bare Wikidata id. */
data class SparqlEntityRef(
    val id: String,
    val kind: EntryKind,
)

/**
 * Runs a user-supplied SPARQL query against the public Wikidata Query Service, per the s-dbm.3
 * spec: the query is expected to bind `?item` and/or `?lexeme` to entity URIs (the form's hint
 * text tells the user so). Any other shape, or a request/parse failure, yields no results rather
 * than throwing — a bad query surfaces as an empty list to build on top of, same fail-open
 * philosophy as [WikidataExistenceChecker], instead of crashing the flow.
 */
class WikidataSparqlClient
    @Inject
    constructor(
        private val httpClient: HttpClient,
    ) {
        suspend fun execute(query: String): List<SparqlEntityRef> =
            runCatching {
                val response =
                    httpClient.get(SPARQL_ENDPOINT) {
                        parameter("query", query)
                        parameter("format", "json")
                    }
                if (!response.status.isSuccess()) return@runCatching emptyList()
                val parsed = sparqlJson.decodeFromString<SparqlResultsResponse>(response.bodyAsText())
                parsed.results.bindings.mapNotNull { it.toEntityRef() }
            }.getOrDefault(emptyList())

        private fun Map<String, SparqlBindingValue>.toEntityRef(): SparqlEntityRef? {
            this["item"]?.value?.let { return uriToId(it)?.let { id -> SparqlEntityRef(id, EntryKind.ITEM) } }
            this["lexeme"]?.value?.let { return uriToId(it)?.let { id -> SparqlEntityRef(id, EntryKind.FORM) } }
            return null
        }

        private fun uriToId(uri: String): String? =
            uri.takeIf { it.startsWith(ENTITY_URI_PREFIX) }?.removePrefix(ENTITY_URI_PREFIX)
    }

@Serializable
private data class SparqlResultsResponse(
    val results: SparqlResults = SparqlResults(),
)

@Serializable
private data class SparqlResults(
    val bindings: List<Map<String, SparqlBindingValue>> = emptyList(),
)

@Serializable
private data class SparqlBindingValue(
    val value: String,
)
