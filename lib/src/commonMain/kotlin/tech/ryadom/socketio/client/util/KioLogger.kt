package tech.ryadom.socketio.client.util

interface KioLogger {

    fun debug(message: () -> String) {
        log(level = LogLevel.DEBUG, message = message(), cause = null)
    }

    fun info(message: () -> String) {
        log(level = LogLevel.INFO, message = message(), cause = null)
    }

    fun warn(cause: Throwable? = null, message: () -> String) {
        log(level = LogLevel.WARN, message = message(), cause = cause)
    }

    fun error(cause: Throwable? = null, message: () -> String) {
        log(level = LogLevel.ERROR, message = message(), cause = cause)
    }

    fun log(level: LogLevel, message: String, cause: Throwable?)

}

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR;
}