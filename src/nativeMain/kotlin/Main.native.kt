import io.github.oshai.kotlinlogging.FormattingAppender
import io.github.oshai.kotlinlogging.KLoggingEvent
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.posix.fprintf
import platform.posix.stderr
import kotlin.system.exitProcess

actual fun debugLogSet() {
    KotlinLoggingConfiguration.logLevel = Level.DEBUG
}

actual fun exitGracefully() {
    exitProcess(0)
}

@OptIn(ExperimentalForeignApi::class)
actual fun logAppenderSet() {
    KotlinLoggingConfiguration.appender = object : FormattingAppender() {
        override fun logFormattedMessage(loggingEvent: KLoggingEvent, formattedMessage: Any?) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val timeStr =
                "${now.year.toString().padStart(4, '0')}-${
                    now.monthNumber.toString().padStart(2, '0')
                }-${now.dayOfMonth.toString().padStart(2, '0')} ${
                    now.hour.toString().padStart(2, '0')
                }:${now.minute.toString().padStart(2, '0')}:${
                    now.second.toString().padStart(2, '0')
                },${(now.nanosecond / 1000000).toString().padStart(3, '0')}"
            if (loggingEvent.level == Level.ERROR) {
                fprintf(stderr, "$timeStr: $formattedMessage\n")
            } else {
                println("$timeStr: $formattedMessage")
            }
        }
    }
}
