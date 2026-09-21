package wiki.asaf.wikisayit.data.wikidata

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import wiki.asaf.wikisayit.data.local.settings.DEFAULT_MAX_LIST_SIZE
import wiki.asaf.wikisayit.ui.session.EntryKind
import javax.inject.Inject

private const val SPARQL_ENDPOINT = "https://query.wikidata.org/sparql"
private const val ENTITY_URI_PREFIX = "http://www.wikidata.org/entity/"

/** Matches a top-level `LIMIT nnnn` clause, case-insensitively. */
private val LIMIT_CLAUSE_REGEX = Regex("""(?i)\bLIMIT\s+(\d+)\b""")

/**
 * Enforces [resultLimit] on a user-supplied SPARQL query, per the s-53x spec: an existing `LIMIT`
 * larger than [resultLimit] is lowered to match, a smaller one is left alone (it already trumps
 * the config), and a query with no `LIMIT` at all gets one appended — both to save response size
 * and to keep list-sourcing bounded.
 *
 * [resultLimit] is the caller's candidate pool, not the final list size: a filtering build
 * (s-3ux) over-fetches deliberately and cuts the list down to size afterwards.
 */
internal fun applyMaxListSize(
    query: String,
    resultLimit: Int,
): String {
    val lastMatch = LIMIT_CLAUSE_REGEX.findAll(query).lastOrNull()
    if (lastMatch == null) return "${query.trimEnd()}\nLIMIT $resultLimit"
    val existingLimit = lastMatch.groupValues[1].toIntOrNull() ?: return query
    if (existingLimit <= resultLimit) return query
    return query.replaceRange(lastMatch.range, "LIMIT $resultLimit")
}

private val sparqlJson = Json { ignoreUnknownKeys = true }

/** One `?item`/`?lexeme` binding from a SPARQL result row, resolved to a bare Wikidata id. */
data class SparqlEntityRef(
    val id: String,
    val kind: EntryKind,
)

/** [SparqlEntityRef]s from a query, plus whether the request itself failed (network error, bad
 * HTTP status, or an unparseable response) as opposed to the query genuinely matching nothing —
 * the two look identical from [refs] alone, so callers that need to tell them apart use [hadError]. */
data class SparqlQueryResult(
    val refs: List<SparqlEntityRef>,
    val hadError: Boolean,
)

/**
 * Runs a user-supplied SPARQL query against the public Wikidata Query Service, per the s-dbm.3
 * spec: the query is expected to bind `?item` and/or `?lexeme` to entity URIs (the form's hint
 * text tells the user so). A query of any other shape yields no results without that being an
 * error — same fail-open philosophy as [WikidataExistenceChecker]. A request/parse failure also
 * yields no results (nothing to build on top of), but is reported via [SparqlQueryResult.hadError]
 * so callers can tell "genuinely no matches" apart from "couldn't find out."
 */
class WikidataSparqlClient
    @Inject
    constructor(
        private val httpClient: HttpClient,
    ) {
        suspend fun execute(
            query: String,
            resultLimit: Int = DEFAULT_MAX_LIST_SIZE,
        ): SparqlQueryResult {
            val result =
                runCatching {
                    val response =
                        httpClient.get(SPARQL_ENDPOINT) {
                            parameter("query", applyMaxListSize(query, resultLimit))
                            parameter("format", "json")
                        }
                    if (!response.status.isSuccess()) return@runCatching null
                    val parsed = sparqlJson.decodeFromString<SparqlResultsResponse>(response.bodyAsText())
                    parsed.results.bindings.mapNotNull { it.toEntityRef() }
                }.getOrNull()
            return SparqlQueryResult(result ?: emptyList(), hadError = result == null)
        }

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
