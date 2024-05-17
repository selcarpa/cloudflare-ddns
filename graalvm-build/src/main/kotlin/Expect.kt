import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import ch.qos.logback.core.ConsoleAppender
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess

fun debugLogSet() {
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

    val logConsoleAppender: ConsoleAppender<ILoggingEvent> = ConsoleAppender<ILoggingEvent>()
    logConsoleAppender.context = logCtx
    logConsoleAppender.name = "console"
    logConsoleAppender.encoder = logEncoder
    logConsoleAppender.start()
    log.addAppender(logConsoleAppender as Appender<ILoggingEvent>)

}

fun logAppenderSet() {}

fun exitGracefully() {
    exitProcess(0)
}

