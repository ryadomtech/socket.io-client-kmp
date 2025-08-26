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

package tech.ryadom.socketio.client.util

/**
 * Helper class for managing event listeners.
 *
 * This object provides a utility function to register an event listener
 * and return a handle that can be used to unregister the listener later.
 */
object On {

    /**
     * Creates an [Emitter.Listener] on the [Emitter].
     * When the new [Emitter.Listener] is triggered, it will direct call the input [Emitter.Listener].
     *
     * @param emitter an [Emitter]
     * @param event an event name
     * @param listener a listener
     * @return a handle which can be used to off the listener
     */
    fun on(emitter: Emitter, event: String, listener: Emitter.Listener): Handle {
        emitter.on(event, listener)
        return Handle { emitter.off(event, listener) }
    }

    /**
     * A handle that can be used to remove an event listener.
     */
    fun interface Handle {
        fun destroy()
    }
}