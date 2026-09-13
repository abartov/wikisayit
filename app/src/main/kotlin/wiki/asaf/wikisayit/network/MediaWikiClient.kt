package wiki.asaf.wikisayit.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File

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

    /** GETs the site's `action=` API (`w/api.php`) with [parameters]; `format=json` is added automatically. */
    suspend inline fun <reified T> getAction(parameters: Map<String, String>): T {
        val token = authTokenProvider.currentAccessToken()
        val response =
            httpClient.get(site.actionApiBaseUrl) {
                parameter("format", "json")
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

    /**
     * POSTs [parameters] as `application/x-www-form-urlencoded` to the site's `action=` API
     * (`w/api.php`) — how write modules like `wbcreateclaim` expect their arguments, as opposed
     * to [post]'s JSON body (for the REST API) or [postActionMultipart]'s file part.
     * `format=json` is added automatically.
     */
    suspend inline fun <reified T> postAction(parameters: Map<String, String>): T {
        val token = authTokenProvider.currentAccessToken()
        val response =
            httpClient.submitForm(
                url = site.actionApiBaseUrl,
                formParameters =
                    Parameters.build {
                        parameters.forEach { (key, value) -> append(key, value) }
                    },
            ) {
                parameter("format", "json")
                token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
        return response.decodeOrThrow()
    }

    /**
     * POSTs [parameters] plus [file] as `multipart/form-data` to the site's `action=` API
     * (`w/api.php`) — needed for `action=upload`, since core REST API has no file upload
     * endpoint. `format=json` is added automatically.
     */
    suspend inline fun <reified T> postActionMultipart(
        parameters: Map<String, String>,
        fileFieldName: String,
        file: File,
        fileContentType: ContentType,
    ): T {
        val token = authTokenProvider.currentAccessToken()
        val response =
            httpClient.submitFormWithBinaryData(
                url = site.actionApiBaseUrl,
                formData =
                    formData {
                        parameters.forEach { (key, value) -> append(key, value) }
                        append(
                            fileFieldName,
                            file.readBytes(),
                            Headers.build {
                                append(HttpHeaders.ContentType, fileContentType.toString())
                                append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                            },
                        )
                    },
            ) {
                parameter("format", "json")
                token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
        return response.decodeOrThrow()
    }

    /**
     * Fetches a fresh CSRF token via `action=query&meta=tokens&type=csrf`, required before any
     * write action (`action=upload`, `wbcreateclaim`, ...). A hardcoded placeholder doesn't work
     * here: this app authenticates with OAuth 2.0 bearer tokens, and the server rejects a token
     * it didn't just hand out.
     */
    suspend fun fetchCsrfToken(): String {
        val response = getAction<CsrfTokenResponse>(mapOf("action" to "query", "meta" to "tokens", "type" to "csrf"))
        return requireNotNull(response.query?.tokens?.csrftoken) { "No CSRF token in tokens response" }
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

@Serializable
data class CsrfTokenResponse(val query: CsrfTokenQuery? = null)

@Serializable
data class CsrfTokenQuery(val tokens: CsrfTokens? = null)

@Serializable
data class CsrfTokens(val csrftoken: String? = null)

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
