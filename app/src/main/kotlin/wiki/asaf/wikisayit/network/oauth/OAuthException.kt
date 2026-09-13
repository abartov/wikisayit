package wiki.asaf.wikisayit.network.oauth

/** Thrown for OAuth 2.0 protocol errors — a token endpoint error body, or a failed callback. */
class OAuthException(val errorCode: String, val errorDescription: String?) :
    Exception(errorDescription ?: errorCode)
