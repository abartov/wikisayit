package wiki.asaf.wikisayit.network.oauth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {
    @Test
    fun `generateCodeVerifier produces distinct url-safe values`() {
        val first = Pkce.generateCodeVerifier()
        val second = Pkce.generateCodeVerifier()

        assertNotEquals(first, second)
        assertTrue(first.none { it == '+' || it == '/' || it == '=' })
    }

    @Test
    fun `deriveCodeChallenge is deterministic and matches RFC 7636 S256 example`() {
        // Verifier/challenge pair from RFC 7636 appendix B.
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        val challenge = Pkce.deriveCodeChallenge(verifier)

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge)
        assertEquals(challenge, Pkce.deriveCodeChallenge(verifier))
    }

    @Test
    fun `generateState produces distinct values`() {
        assertNotEquals(Pkce.generateState(), Pkce.generateState())
    }
}
