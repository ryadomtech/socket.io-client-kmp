package tech.ryadom.kio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import tech.ryadom.kio.io.flatPrimitive
import tech.ryadom.kio.io.toJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class JsonConversionTest {

    @Test
    fun `toJson wraps primitives`() {
        assertEquals(JsonPrimitive("text"), toJson("text"))
        assertEquals(JsonPrimitive(true), toJson(true))
        assertEquals(JsonPrimitive(42), toJson(42))
        assertEquals(JsonPrimitive(42L), toJson(42L))
        assertEquals(JsonPrimitive(1.5), toJson(1.5))
    }

    @Test
    fun `toJson passes json elements through untouched`() {
        val element = buildJsonObject { put("a", JsonPrimitive(1)) }
        assertSame(element, toJson(element))

        val array = buildJsonArray { add(JsonPrimitive(1)) }
        assertSame(array, toJson(array))
    }

    @Test
    fun `toJson falls back to toString for unknown types`() {
        data class Point(val x: Int, val y: Int)
        assertEquals(JsonPrimitive("Point(x=1, y=2)"), toJson(Point(1, 2)))
    }

    @Test
    fun `flatPrimitive unwraps strings`() {
        assertEquals("text", JsonPrimitive("text").flatPrimitive())
        assertEquals("42", JsonPrimitive("42").flatPrimitive())
        assertEquals("true", JsonPrimitive("true").flatPrimitive())
    }

    @Test
    fun `flatPrimitive unwraps booleans`() {
        assertEquals(true, JsonPrimitive(true).flatPrimitive())
        assertEquals(false, JsonPrimitive(false).flatPrimitive())
    }

    @Test
    fun `flatPrimitive unwraps integral numbers to the narrowest type`() {
        assertEquals(1, JsonPrimitive(1).flatPrimitive())
        assertIs<Int>(JsonPrimitive(1).flatPrimitive())

        val large = Int.MAX_VALUE.toLong() + 1
        assertEquals(large, JsonPrimitive(large).flatPrimitive())
        assertIs<Long>(JsonPrimitive(large).flatPrimitive())
    }

    @Test
    fun `flatPrimitive keeps double precision`() {
        val value = 3.141592653589793
        val unwrapped = JsonPrimitive(value).flatPrimitive()

        assertIs<Double>(unwrapped)
        assertEquals(value, unwrapped)
    }

    @Test
    fun `flatPrimitive keeps json null distinguishable from the string null`() {
        assertSame(JsonNull, JsonNull.flatPrimitive())
        assertEquals("null", JsonPrimitive("null").flatPrimitive())
    }

    @Test
    fun `flatPrimitive leaves structured elements untouched`() {
        val obj = buildJsonObject { put("a", JsonPrimitive(1)) }
        assertSame(obj, obj.flatPrimitive())

        val array = buildJsonArray { add(JsonPrimitive(1)) }
        assertSame(array, array.flatPrimitive())
    }

    @Test
    fun `flatPrimitive round trips values parsed from the wire`() {
        val parsed = Json.parseToJsonElement("""[1, 2147483648, 1.5, "s", true, null]""")
            .let { it as kotlinx.serialization.json.JsonArray }
            .map { it.flatPrimitive() }

        assertEquals(listOf(1, 2147483648L, 1.5, "s", true, JsonNull), parsed)
    }
}
