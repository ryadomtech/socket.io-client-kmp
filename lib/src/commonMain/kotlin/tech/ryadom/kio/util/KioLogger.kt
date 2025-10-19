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

package tech.ryadom.kio.util

/**
 * A simple logging facade for kio.
 */
fun interface KioLogger {

    /**
     * Logs a debug message.
     */
    fun debug(message: () -> String) {
        log(level = LogLevel.DEBUG, message = message(), cause = null)
    }

    /**
     * Logs an info message.
     */
    fun info(message: () -> String) {
        log(level = LogLevel.INFO, message = message(), cause = null)
    }

    /**
     * Logs a warning message.
     */
    fun warn(cause: Throwable? = null, message: () -> String) {
        log(level = LogLevel.WARN, message = message(), cause = cause)
    }

    /**
     * Logs an error message.
     */
    fun error(cause: Throwable? = null, message: () -> String) {
        log(level = LogLevel.ERROR, message = message(), cause = cause)
    }

    fun log(level: LogLevel, message: String, cause: Throwable?)

}

/**
 * The log level.
 *
 * @property priority the priority of the log level, the higher the more important.
 */
enum class LogLevel(val priority: Int) {
    /**
     * Debug log level.
     */
    DEBUG(0),

    /**
     * Info log level.
     */
    INFO(1),

    /**
     * Warn log level.
     */
    WARN(2),

    /**
     * Error log level.
     */
    ERROR(3);

    fun isGreaterOrEqualsThan(level: LogLevel): Boolean {
        return priority >= level.priority
    }
}

internal data object NaiveLogger : KioLogger {
    override fun log(level: LogLevel, message: String, cause: Throwable?) {
        println("[$level]: $message")
        cause?.printStackTrace()
    }
}