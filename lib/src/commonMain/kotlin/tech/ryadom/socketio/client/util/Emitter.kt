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

import kotlinx.atomicfu.atomic

/**
 * Represents a thread-safe event emitter.
 * This class allows registering listeners for specific events and emitting events with arguments.
 * Listeners can be registered to be called multiple times (`on`) or only once (`once`).
 * It provides methods to remove listeners individually, by event, or all listeners at once.
 * The implementation ensures thread safety for all operations.
 */
open class Emitter {
    fun interface Listener {
        fun call(vararg args: Any)
    }

    /**
     * Represents the internal state of the Emitter, storing registered listeners.
     * This class is designed to be immutable to ensure thread safety when updating the Emitter's state.
     *
     * @property callbacks A map where keys are event names and values are lists of [Listener]
     *                     instances that will be invoked multiple times for the corresponding event.
     * @property onceCallbacks A map where keys are event names and values are lists of [Listener]
     *                         instances that will be invoked only once for the corresponding event
     *                         and then removed.
     */
    private data class ThreadSafeState(
        val callbacks: Map<String, List<Listener>> = mapOf(),
        val onceCallbacks: Map<String, List<Listener>> = mapOf()
    )

    private val threadSafeState = atomic(
        ThreadSafeState()
    )

    fun on(event: String, listener: Listener): Emitter = apply {
        updateState { current ->
            val updated = current.callbacks.getOrElse(event) { listOf() } + listener
            current.copy(callbacks = current.callbacks + (event to updated))
        }
    }

    fun on(event: String, block: (Array<out Any>) -> Unit): Emitter {
        return on(event, listener = block)
    }

    fun once(event: String, listener: Listener): Emitter = apply {
        updateState { current ->
            val updated = current.onceCallbacks.getOrElse(event) { listOf() } + listener
            current.copy(onceCallbacks = current.onceCallbacks + (event to updated))
        }
    }

    fun once(event: String, block: (Array<out Any>) -> Unit): Emitter {
        return once(event, listener = block)
    }

    fun off(): Emitter = apply {
        threadSafeState.value = ThreadSafeState()
    }

    fun off(event: String): Emitter = apply {
        updateState {
            it.copy(
                callbacks = it.callbacks - event,
                onceCallbacks = it.onceCallbacks - event
            )
        }
    }

    fun off(event: String, fn: Listener): Emitter = apply {
        updateState { current ->
            val newCallbacks = current.callbacks[event]
                ?.filterNot { it === fn }
                .takeIf { !it.isNullOrEmpty() }

            val newOnceCallbacks = current.onceCallbacks[event]
                ?.filterNot { it === fn }
                .takeIf { !it.isNullOrEmpty() }

            current.copy(
                callbacks = updateMap(current.callbacks, event, newCallbacks),
                onceCallbacks = updateMap(current.onceCallbacks, event, newOnceCallbacks)
            )
        }
    }

    open fun emit(event: String, vararg args: Any): Emitter = apply {
        val (callbacks, onceCallbacks) = threadSafeState.value.let {
            it.callbacks[event].orEmpty() to it.onceCallbacks[event].orEmpty()
        }

        if (callbacks.isNotEmpty() || onceCallbacks.isNotEmpty()) {
            // Execute regular callbacks
            callbacks.forEach { it.call(*args) }

            // Execute once callbacks
            onceCallbacks.forEach { it.call(*args) }

            // Remove once callbacks
            updateState { current ->
                current.copy(onceCallbacks = current.onceCallbacks - event)
            }
        }
    }

    fun listeners(event: String): List<Listener> {
        val s = threadSafeState.value
        return s.callbacks[event].orEmpty() + s.onceCallbacks[event].orEmpty()
    }

    fun hasListeners(event: String): Boolean {
        val s = threadSafeState.value
        return s.callbacks.containsKey(event) || s.onceCallbacks.containsKey(event)
    }

    // Helper functions
    private inline fun updateState(updater: (ThreadSafeState) -> ThreadSafeState) {
        while (true) {
            val current = threadSafeState.value
            val updated = updater(current)
            if (threadSafeState.compareAndSet(current, updated)) return
        }
    }

    private fun updateMap(
        map: Map<String, List<Listener>>,
        key: String,
        newValue: List<Listener>?
    ): Map<String, List<Listener>> {
        return if (newValue != null) {
            map + (key to newValue)
        } else {
            map - key
        }
    }
}