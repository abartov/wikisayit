package wiki.asaf.wikisayit.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Required by the Wikimedia User-Agent policy
 * (https://meta.wikimedia.org/wiki/User-Agent_policy): requests without a descriptive
 * User-Agent identifying the client and a contact point are liable to be throttled or blocked. */
private const val WIKISAYIT_USER_AGENT = "WikiSayIt/0.1.0 (https://github.com/abartov/wikisayit)"

/** Builds the single [HttpClient] instance shared by all [MediaWikiClient]s. */
fun createMediaWikiHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        expectSuccess = false

        install(UserAgent) {
            agent = WIKISAYIT_USER_AGENT
        }

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                },
            )
        }

        install(Logging) {
            level = LogLevel.INFO
        }
    }
