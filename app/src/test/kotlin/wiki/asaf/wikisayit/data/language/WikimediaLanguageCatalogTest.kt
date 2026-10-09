package wiki.asaf.wikisayit.data.language

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikimediaLanguageCatalogTest {
    private val response =
        Json { ignoreUnknownKeys = true }.decodeFromString<LanguageInfoResponse>(
            """
            {"batchcomplete":"","query":{"languageinfo":{
              "dag":{"code":"dag","autonym":"dagbanli","name":"Dagbani"},
              "he":{"code":"he","autonym":"עברית","name":"Hebrew"},
              "zh-min-nan":{"code":"zh-min-nan","autonym":"閩南語 / Bân-lâm-gí","name":"Minnan"},
              "qqq":{"code":"qqq","autonym":"","name":"Message documentation"}
            }}}
            """.trimIndent(),
        )

    // CLDR knows Hebrew (in the test JVM's locale, pretend it answers in English) but not Dagbani.
    private val options = parseLanguageInfo(response) { code -> if (code == "he") "Hebrew" else null }

    @Test
    fun `keeps languages that have only a three-letter code`() {
        val dagbani = options.single { it.isoCode == "dag" }
        assertEquals("Dagbani", dagbani.displayName)
        assertEquals("Dagbani", dagbani.englishName)
        assertEquals("dagbanli", dagbani.autonym)
    }

    @Test
    fun `drops the message-documentation pseudo-language`() {
        assertNull(options.find { it.isoCode == "qqq" })
    }

    @Test
    fun `prefers the phone-locale name where CLDR has one`() {
        val response =
            Json.decodeFromString<LanguageInfoResponse>(
                """{"query":{"languageinfo":{"he":{"code":"he","autonym":"עברית","name":"Hebrew"}}}}""",
            )
        val he = parseLanguageInfo(response) { "עברית" }.single()
        assertEquals("עברית", he.displayName)
        assertEquals("Hebrew", he.englishName)
    }

    @Test
    fun `a response with no languages yields no options`() {
        assertTrue(parseLanguageInfo(LanguageInfoResponse()).isEmpty())
    }

    @Test
    fun `search finds a language by English name, autonym, or code, prefix matches first`() {
        assertEquals(listOf("dag"), LanguageCatalog.search(options, "dagb").map { it.isoCode })
        assertEquals(listOf("dag"), LanguageCatalog.search(options, "dagbanli").map { it.isoCode })
        assertEquals(listOf("dag"), LanguageCatalog.search(options, "DAG").map { it.isoCode })
        assertEquals(listOf("zh-min-nan"), LanguageCatalog.search(options, "nan").map { it.isoCode })
        assertEquals(options.size, LanguageCatalog.search(options, "").size)
    }

    @Test
    fun `installed Wikimedia list supplies English names for three-letter codes`() {
        LanguageCatalog.install(options)
        try {
            assertEquals("Dagbani", LanguageCatalog.englishName("dag"))
            assertEquals("Hebrew", LanguageCatalog.englishName("he"))
            assertNotNull(LanguageCatalog.languages.value)
        } finally {
            LanguageCatalog.resetForTest()
        }
    }

    @Test
    fun `English name falls back to the code when nobody knows the language`() {
        assertEquals("zzz", LanguageCatalog.englishName("zzz"))
    }
}
