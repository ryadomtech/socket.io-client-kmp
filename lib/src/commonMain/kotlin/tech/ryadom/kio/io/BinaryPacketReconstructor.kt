/*
    MIT License

    Copyright (c) 2025 Ryadom Tech

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.
 */

package tech.ryadom.kio.io

import kotlinx.io.bytestring.ByteString
import org.hildan.socketio.PayloadElement
import org.hildan.socketio.SocketIOPacket

/**
 * Reconstructs a binary packet from its individual parts.
 *
 * This class is responsible for assembling a [SocketIOPacket.BinaryMessage] or [SocketIOPacket.BinaryAck]
 * by collecting its binary attachments. Once all attachments have been received,
 * it reconstructs the original payload and emits it using the provided [emitter].
 *
 * @property packet The initial binary packet metadata, including the expected number of attachments and the payload structure.
 * @property emitter A function to call when the packet is fully reconstructed.
 *                  It receives a boolean indicating if the packet is an acknowledgment, the acknowledgment ID (if any),
 *                  and the reconstructed payload as an ArrayList of Any.
 */
class BinaryPacketReconstructor(
    private val packet: SocketIOPacket.BinaryMessage,
    private val emitter: (isAck: Boolean, ackId: Int?, ArrayList<Any>) -> Unit,
) {
    private val buffers = ArrayList<ByteString>()
    private var currentAttachmentIndex = 0

    /**
     * Adds a binary attachment to the packet.
     *
     * If this is the last expected attachment, the packet is reconstructed,
     * and the [emitter] function is called with the completed data.
     *
     * @param buffer The [ByteString] representing the binary attachment.
     */
    fun add(buffer: ByteString) {
        if (buffers.size < packet.nBinaryAttachments) {
            buffers.ensureCapacity(packet.nBinaryAttachments)
        }

        buffers.add(currentAttachmentIndex++, buffer)

        if (currentAttachmentIndex == packet.nBinaryAttachments) {
            val data = ArrayList<Any>(packet.payload.size)
            packet.payload.forEach { element ->
                when (element) {
                    is PayloadElement.AttachmentRef -> {
                        data.add(buffers[element.attachmentIndex])
                    }

                    is PayloadElement.Json -> data.add(element.jsonElement)
                }
            }
            emitter(packet is SocketIOPacket.BinaryAck, packet.ackId, data)
        }
    }
}
