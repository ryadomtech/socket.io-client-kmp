package tech.ryadom.kio

import tech.ryadom.kio.util.Emitter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmitterTest {

    @Test
    fun `on receives every emission`() {
        val emitter = Emitter()
        val received = mutableListOf<String>()

        emitter.on("event") { received += it.joinToString() }

        emitter.emit("event", "a")
        emitter.emit("event", "b")

        assertEquals(listOf("a", "b"), received)
    }

    @Test
    fun `on passes all arguments through`() {
        val emitter = Emitter()
        var args: Array<out Any> = emptyArray()

        emitter.on("event") { args = it }
        emitter.emit("event", 1, "two", true)

        assertEquals(listOf<Any>(1, "two", true), args.toList())
    }

    @Test
    fun `once receives a single emission`() {
        val emitter = Emitter()
        var calls = 0

        emitter.once("event") { calls++ }

        emitter.emit("event")
        emitter.emit("event")

        assertEquals(1, calls)
    }

    @Test
    fun `once re-registered from its own callback survives the emission`() {
        val emitter = Emitter()
        val received = mutableListOf<Int>()
        var round = 0

        fun subscribe() {
            emitter.once("event") {
                received += round
                if (round < 2) subscribe()
            }
        }
        subscribe()

        round = 1
        emitter.emit("event")
        round = 2
        emitter.emit("event")
        round = 3
        emitter.emit("event")

        assertEquals(listOf(1, 2), received)
    }

    @Test
    fun `once for another event registered during dispatch is preserved`() {
        val emitter = Emitter()
        var reached = false

        emitter.once("first") {
            emitter.once("second") { reached = true }
        }

        emitter.emit("first")
        emitter.emit("second")

        assertTrue(reached)
    }

    @Test
    fun `emitting an event without listeners is a no-op`() {
        val emitter = Emitter()
        emitter.emit("nobody-listens")
        assertFalse(emitter.hasListeners("nobody-listens"))
    }

    @Test
    fun `onAny receives every event`() {
        val emitter = Emitter()
        val seen = mutableListOf<String>()

        emitter.onAny { seen += it.first().toString() }

        emitter.emit("a", "1")
        emitter.emit("b", "2")

        assertEquals(listOf("1", "2"), seen)
    }

    @Test
    fun `offAny stops any-listener`() {
        val emitter = Emitter()
        var calls = 0
        val listener = Emitter.Listener { calls++ }

        emitter.onAny(listener)
        emitter.emit("a")
        emitter.offAny(listener)
        emitter.emit("a")

        assertEquals(1, calls)
    }

    @Test
    fun `off by listener removes only that listener`() {
        val emitter = Emitter()
        var first = 0
        var second = 0
        val firstListener = Emitter.Listener { first++ }
        val secondListener = Emitter.Listener { second++ }

        emitter.on("event", firstListener)
        emitter.on("event", secondListener)
        emitter.off("event", firstListener)
        emitter.emit("event")

        assertEquals(0, first)
        assertEquals(1, second)
    }

    @Test
    fun `off by listener also removes a once listener`() {
        val emitter = Emitter()
        var calls = 0
        val listener = Emitter.Listener { calls++ }

        emitter.once("event", listener)
        emitter.off("event", listener)
        emitter.emit("event")

        assertEquals(0, calls)
        assertFalse(emitter.hasListeners("event"))
    }

    @Test
    fun `off by event removes both kinds of listeners`() {
        val emitter = Emitter()
        var calls = 0

        emitter.on("event") { calls++ }
        emitter.once("event") { calls++ }
        emitter.off("event")
        emitter.emit("event")

        assertEquals(0, calls)
        assertFalse(emitter.hasListeners("event"))
    }

    @Test
    fun `off without arguments clears everything`() {
        val emitter = Emitter()
        var calls = 0

        emitter.on("a") { calls++ }
        emitter.once("b") { calls++ }
        emitter.onAny { calls++ }

        emitter.off()

        emitter.emit("a")
        emitter.emit("b")

        assertEquals(0, calls)
    }

    @Test
    fun `listeners reports registered listeners for an event`() {
        val emitter = Emitter()
        val onListener = Emitter.Listener { }
        val onceListener = Emitter.Listener { }

        emitter.on("event", onListener)
        emitter.once("event", onceListener)

        assertEquals(listOf(onListener, onceListener), emitter.listeners("event"))
        assertEquals(emptyList(), emitter.listeners("other"))
    }

    @Test
    fun `listeners registered for one event do not leak into another`() {
        val emitter = Emitter()
        var calls = 0

        emitter.on("a") { calls++ }
        emitter.emit("b")

        assertEquals(0, calls)
    }

    @Test
    fun `every listener of an event is invoked in registration order`() {
        val emitter = Emitter()
        val order = mutableListOf<Int>()

        emitter.on("event") { order += 1 }
        emitter.on("event") { order += 2 }
        emitter.once("event") { order += 3 }

        emitter.emit("event")

        assertEquals(listOf(1, 2, 3), order)
    }
}
