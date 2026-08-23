package tech.ryadom.kio

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.serialization.json.JsonPrimitive
import org.hildan.socketio.PayloadElement
import org.hildan.socketio.SocketIOPacket
import tech.ryadom.kio.io.BinaryPacketReconstructor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BinaryPacketReconstructorTest {

    private class Capture {
        var isAck: Boolean? = null
        var ackId: Int? = null
        var data: List<Any>? = null
        var calls = 0

        fun emitter(): (Boolean, Int?, ArrayList<Any>) -> Unit = { ack, id, payload ->
            isAck = ack
            ackId = id
            data = payload
            calls++
        }
    }

    private fun binaryEvent(
        payload: List<PayloadElement>,
        attachments: Int,
        ackId: Int? = null
    ) = SocketIOPacket.BinaryEvent(
        namespace = "/",
        ackId = ackId,
        payload = payload,
        nBinaryAttachments = attachments
    )

    @Test
    fun `emits once every attachment arrived`() {
        val capture = Capture()
        val first = "one".encodeToByteString()
        val second = "two".encodeToByteString()

        val reconstructor = BinaryPacketReconstructor(
            binaryEvent(
                payload = listOf(
                    PayloadElement.Json(JsonPrimitive("event")),
                    PayloadElement.AttachmentRef(0),
                    PayloadElement.AttachmentRef(1)
                ),
                attachments = 2
            ),
            capture.emitter()
        )

        reconstructor.add(first)
        assertEquals(0, capture.calls)

        reconstructor.add(second)
        assertEquals(1, capture.calls)
        assertEquals(listOf(JsonPrimitive("event"), first, second), capture.data)
        assertEquals(false, capture.isAck)
        assertNull(capture.ackId)
    }

    @Test
    fun `keeps the original payload order`() {
        val capture = Capture()
        val buffer = "bin".encodeToByteString()

        BinaryPacketReconstructor(
            binaryEvent(
                payload = listOf(
                    PayloadElement.Json(JsonPrimitive("event")),
                    PayloadElement.AttachmentRef(0),
                    PayloadElement.Json(JsonPrimitive("tail"))
                ),
                attachments = 1
            ),
            capture.emitter()
        ).add(buffer)

        assertEquals(
            listOf(JsonPrimitive("event"), buffer, JsonPrimitive("tail")),
            capture.data
        )
    }

    @Test
    fun `a packet without attachments is complete on arrival`() {
        val capture = Capture()

        val reconstructor = BinaryPacketReconstructor(
            binaryEvent(
                payload = listOf(PayloadElement.Json(JsonPrimitive("event"))),
                attachments = 0
            ),
            capture.emitter()
        )

        assertTrue(reconstructor.isComplete)

        reconstructor.emitIfComplete()
        assertEquals(1, capture.calls)
        assertEquals(listOf(JsonPrimitive("event")), capture.data)
    }

    @Test
    fun `extra attachments are ignored`() {
        val capture = Capture()

        val reconstructor = BinaryPacketReconstructor(
            binaryEvent(
                payload = listOf(
                    PayloadElement.Json(JsonPrimitive("event")),
                    PayloadElement.AttachmentRef(0)
                ),
                attachments = 1
            ),
            capture.emitter()
        )

        reconstructor.add("a".encodeToByteString())
        reconstructor.add("b".encodeToByteString())

        assertEquals(1, capture.calls)
    }

    @Test
    fun `an out of range attachment reference is skipped instead of crashing`() {
        val capture = Capture()

        BinaryPacketReconstructor(
            binaryEvent(
                payload = listOf(
                    PayloadElement.Json(JsonPrimitive("event")),
                    PayloadElement.AttachmentRef(7)
                ),
                attachments = 1
            ),
            capture.emitter()
        ).add("a".encodeToByteString())

        assertEquals(1, capture.calls)
        assertEquals(listOf(JsonPrimitive("event")), capture.data)
    }

    @Test
    fun `a binary ack reports itself as an ack with its id`() {
        val capture = Capture()

        BinaryPacketReconstructor(
            SocketIOPacket.BinaryAck(
                namespace = "/",
                ackId = 42,
                payload = listOf(PayloadElement.AttachmentRef(0)),
                nBinaryAttachments = 1
            ),
            capture.emitter()
        ).add(ByteString(1, 2, 3))

        assertEquals(true, capture.isAck)
        assertEquals(42, capture.ackId)
    }

    @Test
    fun `isComplete tracks the collected attachments`() {
        val reconstructor = BinaryPacketReconstructor(
            binaryEvent(
                payload = listOf(PayloadElement.AttachmentRef(0), PayloadElement.AttachmentRef(1)),
                attachments = 2
            )
        ) { _, _, _ -> }

        assertFalse(reconstructor.isComplete)
        reconstructor.add("a".encodeToByteString())
        assertFalse(reconstructor.isComplete)
        reconstructor.add("b".encodeToByteString())
        assertTrue(reconstructor.isComplete)
    }
}
