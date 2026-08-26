package tech.ryadom.kio

import tech.ryadom.kio.util.UriUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UriUtilsTest {

    @Test
    fun `encode keeps unreserved characters untouched`() {
        val unreserved = "ABCXYZabcxyz0189-_.!~*'()"
        assertEquals(unreserved, UriUtils.encode(unreserved))
    }

    @Test
    fun `encode percent encodes reserved characters`() {
        assertEquals("a%20b", UriUtils.encode("a b"))
        assertEquals("%26", UriUtils.encode("&"))
        assertEquals("%3D", UriUtils.encode("="))
        assertEquals("%3F", UriUtils.encode("?"))
        assertEquals("%2F", UriUtils.encode("/"))
        assertEquals("%25", UriUtils.encode("%"))
        assertEquals("%2B", UriUtils.encode("+"))
    }

    @Test
    fun `encode uses utf8 bytes for non ascii characters`() {
        assertEquals("%D0%9F", UriUtils.encode("П"))
        assertEquals("%E2%82%AC", UriUtils.encode("€"))
        assertEquals("%F0%9F%9A%80", UriUtils.encode("🚀"))
    }

    @Test
    fun `decode restores ascii characters`() {
        assertEquals("a b", UriUtils.decode("a%20b"))
        assertEquals("hello!", UriUtils.decode("hello%21"))
        assertEquals("https://example.com", UriUtils.decode("https%3A%2F%2Fexample.com"))
    }

    @Test
    fun `decode restores multi byte utf8 sequences`() {
        assertEquals("П", UriUtils.decode("%D0%9F"))
        assertEquals("Привет", UriUtils.decode("%D0%9F%D1%80%D0%B8%D0%B2%D0%B5%D1%82"))
        assertEquals("€", UriUtils.decode("%E2%82%AC"))
        assertEquals("🚀", UriUtils.decode("%F0%9F%9A%80"))
    }

    @Test
    fun `decode restores mixed ascii and multi byte sequences`() {
        assertEquals("a П b", UriUtils.decode("a%20%D0%9F%20b"))
        assertEquals("цена: 10€", UriUtils.decode("%D1%86%D0%B5%D0%BD%D0%B0%3A%2010%E2%82%AC"))
    }

    @Test
    fun `decode maps plus to space`() {
        assertEquals("a b c", UriUtils.decode("a+b+c"))
    }

    @Test
    fun `decode is symmetric with encode`() {
        listOf(
            "простой текст",
            "a b&c=d",
            "🚀 rocket",
            "mixed Пример 123 !~*'()",
            ""
        ).forEach {
            assertEquals(it, UriUtils.decode(UriUtils.encode(it)), "round trip of '$it'")
        }
    }

    @Test
    fun `decode rejects truncated escape sequences`() {
        listOf("%", "%2", "100%", "%D0%9").forEach {
            assertFailsWith<IllegalArgumentException>("expected '$it' to be rejected") {
                UriUtils.decode(it)
            }
        }
    }

    @Test
    fun `decode rejects non hexadecimal escape sequences`() {
        assertFailsWith<IllegalArgumentException> { UriUtils.decode("%ZZ") }
        assertFailsWith<IllegalArgumentException> { UriUtils.decode("a%2Zb") }
    }
}
