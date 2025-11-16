package tech.ryadom.kio

import tech.ryadom.kio.util.QSParsingUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QSParsingUtilsTest {

    @Test
    fun `encode should return empty string for empty map`() {
        val result = QSParsingUtils.encode(emptyMap())
        assertEquals("", result)
    }

    @Test
    fun `encode should handle single key-value pair`() {
        val input = mapOf("key" to "value")
        val result = QSParsingUtils.encode(input)
        assertEquals("key=value", result)
    }

    @Test
    fun `encode should handle multiple key-value pairs`() {
        val input = mapOf(
            "name" to "John Doe",
            "age" to "30",
            "city" to "New York"
        )
        val result = QSParsingUtils.encode(input)

        // Result should contain all pairs joined by &
        assertTrue(result.contains("name=John%20Doe"))
        assertTrue(result.contains("age=30"))
        assertTrue(result.contains("city=New%20York"))
        assertEquals(2, result.count { it == '&' })
    }

    @Test
    fun `encode should URL encode special characters`() {
        val input = mapOf(
            "special chars" to "hello&world=test",
            "email" to "user@example.com"
        )
        val result = QSParsingUtils.encode(input)

        assertTrue(result.contains("special%20chars=hello%26world%3Dtest"))
        assertTrue(result.contains("email=user%40example.com"))
    }

    @Test
    fun `decode should return empty map for empty string`() {
        val result = QSParsingUtils.decode("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `decode should handle single key-value pair`() {
        val result = QSParsingUtils.decode("key=value")
        assertEquals(mapOf("key" to "value"), result)
    }

    @Test
    fun `decode should handle multiple key-value pairs`() {
        val result = QSParsingUtils.decode("name=John%20Doe&age=30&city=New%20York")

        assertEquals(
            mapOf(
                "name" to "John Doe",
                "age" to "30",
                "city" to "New York"
            ),
            result
        )
    }

    @Test
    fun `decode should handle empty values`() {
        val result = QSParsingUtils.decode("key=&empty=")
        assertEquals(mapOf("key" to "", "empty" to ""), result)
    }

    @Test
    fun `decode should ignore pairs with empty keys`() {
        val result = QSParsingUtils.decode("=value&valid=key&=another")
        assertEquals(mapOf("valid" to "key"), result)
    }

    @Test
    fun `decode should handle URL encoded characters`() {
        val result = QSParsingUtils.decode("message=Hello%20World%21&url=https%3A%2F%2Fexample.com")

        assertEquals(
            mapOf(
                "message" to "Hello World!",
                "url" to "https://example.com"
            ),
            result
        )
    }

    @Test
    fun `decode should handle values with multiple equals signs`() {
        val result = QSParsingUtils.decode("expression=a%3Db%2Bc&equation=x%3Dy%3Dz")

        assertEquals(
            mapOf(
                "expression" to "a=b+c",
                "equation" to "x=y=z"
            ),
            result
        )
    }

    @Test
    fun `decode should handle ampersand in values when encoded`() {
        val result = QSParsingUtils.decode("filter=price%3C100%26available%3Dtrue")
        assertEquals(mapOf("filter" to "price<100&available=true"), result)
    }

    @Test
    fun `encode and decode should be symmetric`() {
        val original = mapOf(
            "name" to "Alice",
            "email" to "alice@example.com",
            "message" to "Hello & Welcome!",
            "empty" to ""
        )

        val encoded = QSParsingUtils.encode(original)
        val decoded = QSParsingUtils.decode(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `decode should handle mixed separators and encoding`() {
        val result = QSParsingUtils.decode("a=1&b=2&c=3%264&d=hello%20world")

        assertEquals(
            mapOf(
                "a" to "1",
                "b" to "2",
                "c" to "3&4",
                "d" to "hello world"
            ),
            result
        )
    }
}