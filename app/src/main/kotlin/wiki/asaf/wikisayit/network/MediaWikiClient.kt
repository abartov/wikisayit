package wiki.asaf.wikisayit.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Thin, authenticated JSON client for one MediaWiki [site]'s REST API.
 *
 * Business-specific endpoints (list sourcing, upload, Wikidata statement edits, ...) are built
 * on top of this in their respective feature modules; this class only owns URL/auth/JSON/error
 * plumbing shared across all of them.
 */
class MediaWikiClient(
    val site: MediaWikiSite,
    val httpClient: HttpClient,
    val authTokenProvider: AuthTokenProvider = NoAuthTokenProvider,
) {
    /** GETs [path] (relative to the site's REST API root) with optional query [parameters]. */
    suspend inline fun <reified T> get(
        path: String,
        parameters: Map<String, String> = emptyMap(),
    ): T {
        val token = authTokenProvider.currentAccessToken()
        val response =
            httpClient.get(resolve(path)) {
                parameters.forEach { (key, value) -> parameter(key, value) }
                token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
        return response.decodeOrThrow()
    }

    /** POSTs a JSON-serializable [body] to [path] (relative to the site's REST API root). */
    suspend inline fun <reified TRequest, reified TResponse> post(
        path: String,
        body: TRequest,
    ): TResponse {
        val token = authTokenProvider.currentAccessToken()
        val response =
            httpClient.post(resolve(path)) {
                contentType(ContentType.Application.Json)
                setBody(body)
                token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
        return response.decodeOrThrow()
    }

    /** Resolves [path] against the site's REST API root, tolerating either leading/trailing slash. */
    fun resolve(path: String): String = site.restApiBaseUrl.trimEnd('/') + "/" + path.trimStart('/')

    suspend inline fun <reified T> HttpResponse.decodeOrThrow(): T {
        if (status.isSuccess()) {
            return body()
        }
        val rawBody = body<String>()
        throw toMediaWikiApiException(status.value, rawBody)
    }
}

private val lenientJson = Json { ignoreUnknownKeys = true }

fun toMediaWikiApiException(
    statusCode: Int,
    rawBody: String,
): MediaWikiApiException {
    val (errorKey, errorMessage) =
        runCatching {
            val json = lenientJson.parseToJsonElement(rawBody).jsonObject
            val key = json.stringField("errorKey") ?: json.stringField("code")
            val message = json.stringField("httpReason") ?: json.stringField("message")
            key to message
        }.getOrDefault(null to null)
    return MediaWikiApiException(statusCode, errorKey, errorMessage, rawBody)
}

private fun JsonObject.stringField(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
