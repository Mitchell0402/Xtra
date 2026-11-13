package com.github.andreyasadchy.xtra.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Only English and Simplified Chinese ship (plan section 12, U1), and the Chinese UI must not fall back to English
 * text: every translatable string has a zh-CN entry that is actually translated (plan 10.6, U1 gate).
 */
class TranslationsTest {
    private val res = File("src/main/res")

    private fun strings(file: File): Map<String, String> =
        Regex("""<string name="([^"]+)"([^>]*)>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .filterNot { "translatable=\"false\"" in it.groupValues[2] }
            .associate { it.groupValues[1] to it.groupValues[3] }

    @Test
    fun onlyEnglishAndSimplifiedChinese() {
        val locales = res.listFiles()!!.map { it.name }.filter { it.startsWith("values-") && it.contains(Regex("^values-[a-z]{2}(-r[A-Z]{2})?$")) }
        assertEquals(listOf("values-zh-rCN"), locales)
    }

    @Test
    fun everyStringIsTranslated() {
        val en = res.resolve("values").listFiles()!!.filter { it.extension == "xml" }.fold(emptyMap<String, String>()) { m, f -> m + strings(f) }
        val zh = res.resolve("values-zh-rCN").listFiles()!!.filter { it.extension == "xml" }.fold(emptyMap<String, String>()) { m, f -> m + strings(f) }
        val missing = en.keys - zh.keys
        assertTrue("missing in zh-CN: $missing", missing.isEmpty())
        // formats, library names and emoji layouts read the same in both languages
        val sameOnPurpose = Regex("^(timestamp_format_\\d|imageLibraryEntries\\d|poll_choice.*|prediction_outcome.*|moderators|vips)$")
        val english = zh.filter { (k, v) ->
            !sameOnPurpose.matches(k) && !v.contains(Regex("[\\u3400-\\u9fff]")) && v.contains(Regex("[A-Za-z]{3,}"))
        }
        assertTrue("zh-CN strings still in English: ${english.keys}", english.isEmpty())
    }
}
