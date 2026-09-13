package wiki.asaf.wikisayit.network.oauth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** RFC 7636 PKCE helpers for the OAuth 2.0 authorization code flow. */
object Pkce {
    private val secureRandom = SecureRandom()

    /** A high-entropy `code_verifier`, kept in memory for the lifetime of one login attempt. */
    fun generateCodeVerifier(): String = randomUrlSafeString(32)

    /** A random `state` value used to reject callbacks that don't match an in-flight request. */
    fun generateState(): String = randomUrlSafeString(16)

    /** The `code_challenge` (S256) sent with the authorize request for a given [codeVerifier]. */
    fun deriveCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun randomUrlSafeString(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
