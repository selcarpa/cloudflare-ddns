import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*

private val logger = KotlinLogging.logger {}
actual fun <T : HttpClientEngineConfig> HttpClientConfig<T>.initLogging() {
    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.BODY
        sanitizeHeader { header -> header == HttpHeaders.Authorization }
    }
}

actual fun info(message: () -> Any?) {
    logger.info { message }
}


actual fun warn(message: () -> Any?) {
    logger.warn { message }
}


actual fun error(message: () -> Any?) {
    logger.error { message }
}


actual fun debug(message: () -> Any?) {
    logger.debug { message }
}