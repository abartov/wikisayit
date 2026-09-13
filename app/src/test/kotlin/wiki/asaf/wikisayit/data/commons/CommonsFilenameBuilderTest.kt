package wiki.asaf.wikisayit.data.commons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonsFilenameBuilderTest {
    @Test
    fun `builds lexeme filename per spec example`() {
        val filename = buildCommonsFilename(isoCode = "uk", entityId = "L708539", label = "мова", username = "Ijon")
        assertEquals("uk-L708539-мова-Ijon.ogg", filename)
    }

    @Test
    fun `builds item filename per spec example`() {
        val filename =
            buildCommonsFilename(isoCode = "he", entityId = "Q432522", label = "יונתן רטוש", username = "Ijon")
        assertEquals("he-Q432522-יונתן רטוש-Ijon.ogg", filename)
    }

    @Test
    fun `strips characters MediaWiki title syntax forbids`() {
        val filename = buildCommonsFilename(isoCode = "en", entityId = "Q1", label = "a[b]{c}#d<e>f|g", username = "u")
        assertEquals("en-Q1-a b c d e f g-u.ogg", filename)
    }

    @Test
    fun `collapses and trims whitespace in the label`() {
        val filename =
            buildCommonsFilename(isoCode = "en", entityId = "Q1", label = "  many   spaces  ", username = "u")
        assertEquals("en-Q1-many spaces-u.ogg", filename)
    }

    @Test
    fun `truncates an overlong label instead of exceeding the title length cap`() {
        val filename =
            buildCommonsFilename(isoCode = "en", entityId = "Q1", label = "x".repeat(500), username = "someone")
        assertTrue(filename.toByteArray(Charsets.UTF_8).size <= 240)
        assertTrue(filename.startsWith("en-Q1-x"))
        assertTrue(filename.endsWith("-someone.ogg"))
    }

    @Test
    fun `never leaves an empty entity id as the literal string null`() {
        val filename = buildCommonsFilename(isoCode = "en", entityId = "", label = "word", username = "u")
        assertFalse(filename.contains("null"))
        assertEquals("en--word-u.ogg", filename)
    }
}
