import io.github.oshai.kotlinlogging.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.posix.fprintf
import platform.posix.stderr
import kotlin.system.exitProcess

actual fun debugLogSet() {
    KotlinLoggingConfiguration.logLevel = Level.ERROR
}

actual fun exitGracefully() {
    exitProcess(1)
}

@OptIn(ExperimentalForeignApi::class)
actual fun logAppenderSet() {
    KotlinLoggingConfiguration.appender= object : FormattingAppender() {
        override fun logFormattedMessage(loggingEvent: KLoggingEvent, formattedMessage: Any?) {
            val timeStr= Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            if (loggingEvent.level == Level.ERROR) {
                fprintf(stderr, "$timeStr: $formattedMessage\n")
            } else {
                println("$timeStr: $formattedMessage")
            }
        }
    }
}
