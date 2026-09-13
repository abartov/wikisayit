package wiki.asaf.wikisayit.network.oauth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject

@Serializable
private data class TokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
private data class OAuthErrorDto(
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

@Serializable
private data class ProfileResponseDto(val username: String)

/** Talks to the Wikimedia OAuth 2.0 endpoints ([OAuthConfig]) for token exchange/refresh and identity lookup. */
class WikimediaOAuthClient
    @Inject
    constructor(
        private val httpClient: HttpClient,
        private val clock: Clock,
    ) {
        suspend fun exchangeAuthorizationCode(
            code: String,
            codeVerifier: String,
        ): OAuthTokens =
            requestTokens(
                Parameters.build {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("redirect_uri", OAuthConfig.REDIRECT_URI)
                    append("client_id", OAuthConfig.CLIENT_ID)
                    append("code_verifier", codeVerifier)
                },
            )

        suspend fun refresh(refreshToken: String): OAuthTokens =
            requestTokens(
                Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refreshToken)
                    append("client_id", OAuthConfig.CLIENT_ID)
                },
            )

        suspend fun fetchAuthenticatedUsername(accessToken: String): String {
            val response =
                httpClient.get(OAuthConfig.PROFILE_URL) {
                    header(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            return response.decodeOrThrow<ProfileResponseDto>().username
        }

        private suspend fun requestTokens(formParameters: Parameters): OAuthTokens {
            val response = httpClient.submitForm(url = OAuthConfig.TOKEN_URL, formParameters = formParameters)
            val dto = response.decodeOrThrow<TokenResponseDto>()
            return OAuthTokens(
                accessToken = dto.accessToken,
                refreshToken = dto.refreshToken,
                expiresAtEpochMillis = clock.millis() + dto.expiresIn * 1000,
            )
        }

        private suspend inline fun <reified T> HttpResponse.decodeOrThrow(): T {
            if (status.isSuccess()) return body()
            val raw = body<String>()
            val parsed = runCatching { lenientJson.decodeFromString<OAuthErrorDto>(raw) }.getOrNull()
            throw OAuthException(parsed?.error ?: "http_${status.value}", parsed?.errorDescription ?: raw)
        }

        private companion object {
            val lenientJson = Json { ignoreUnknownKeys = true }
        }
    }
