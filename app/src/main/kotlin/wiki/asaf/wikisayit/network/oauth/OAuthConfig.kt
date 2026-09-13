package wiki.asaf.wikisayit.network.oauth

/**
 * Wikimedia OAuth 2.0 endpoints and the app's registered public (PKCE) consumer.
 *
 * The consumer is registered at Special:OAuthConsumerRegistration on meta.wikimedia.org with
 * [REDIRECT_URI] as its exact callback URL. A public client's `client_id` is not secret — it's
 * only ever exchanged together with the `redirect_uri` and a fresh PKCE `code_verifier` that
 * never leaves this device — so it's fine to check in rather than treat like the (nonexistent)
 * client secret.
 */
object OAuthConfig {
    const val CLIENT_ID = "afa4b9e6dcf2ba79b6d1c0f97973f833"

    const val REDIRECT_SCHEME = "wiki.asaf.wikisayit"
    const val REDIRECT_URI = "$REDIRECT_SCHEME:/callback"

    const val AUTHORIZE_URL = "https://meta.wikimedia.org/w/rest.php/oauth2/authorize"
    const val TOKEN_URL = "https://meta.wikimedia.org/w/rest.php/oauth2/access_token"
    const val PROFILE_URL = "https://meta.wikimedia.org/w/rest.php/oauth2/resource/profile"
}
