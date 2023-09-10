import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.ConsoleAppender
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.util.logging.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

private var logger = KotlinLogging.logger {}
actual fun <T : HttpClientEngineConfig> HttpClientConfig<T>.initLogging() {
    install(Logging) {
        logger = io.ktor.client.plugins.logging.Logger.DEFAULT
        level = LogLevel.BODY
        sanitizeHeader { header -> header == HttpHeaders.Authorization }
    }
}

actual fun info(message: () -> Any?) {
    logger.info(message)
}


actual fun warn(message: () -> Any?) {
    logger.warn(message)
}


actual fun error(message: () -> Any?) {
    logger.error(message)
}


actual fun debug(message: () -> Any?) {
    logger.debug(message)
}

actual fun debugLogSet() {
    val logCtx: LoggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
    val log = logCtx.getLogger(Logger.ROOT_LOGGER_NAME)
    log.detachAndStopAllAppenders()
    log.level = ch.qos.logback.classic.Level.DEBUG
    log.isAdditive = false
    val logEncoder = PatternLayoutEncoder()
    logEncoder.context = logCtx
    logEncoder.pattern = "%date{ISO8601} %highlight(%level) [%t] %cyan(%logger{16}) %M: %msg%n"
    logEncoder.charset = StandardCharsets.UTF_8
    logEncoder.start()

    val logConsoleAppender: ConsoleAppender<*> = ConsoleAppender<Any?>()
    logConsoleAppender.context = logCtx
    logConsoleAppender.name = "console"
    logConsoleAppender.encoder = logEncoder
    logConsoleAppender.start()
    log.addAppender(logConsoleAppender as Appender<ILoggingEvent>)

}
