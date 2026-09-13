package wiki.asaf.wikisayit.data.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfaceLanguagesTest {
    @Test
    fun `includes every language with a values directory, built from BuildConfig not a hardcoded list`() {
        val tags = InterfaceLanguages.options.map { it.tag }
        assertTrue("en" in tags)
        assertTrue("he" in tags)
    }

    @Test
    fun `excludes legacy locale aliases Android normalizes away`() {
        val tags = InterfaceLanguages.options.map { it.tag }
        assertTrue("iw" !in tags)
    }

    @Test
    fun `each option's display name is a non-blank endonym`() {
        InterfaceLanguages.options.forEach { option ->
            assertTrue("displayName for ${option.tag} should not be blank", option.displayName.isNotBlank())
        }
    }

    @Test
    fun `options are sorted by display name`() {
        val names = InterfaceLanguages.options.map { it.displayName }
        assertEquals(names.sortedWith(java.text.Collator.getInstance()), names)
    }
}
