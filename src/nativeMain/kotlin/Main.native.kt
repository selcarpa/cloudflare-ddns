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
    KotlinLoggingConfiguration.logLevel = Level.ERROR
}

actual fun exitGracefully() {
    exitProcess(1)
}

@OptIn(ExperimentalForeignApi::class)
actual fun logAppenderSet() {
    KotlinLoggingConfiguration.appender = object : FormattingAppender() {
        override fun logFormattedMessage(loggingEvent: KLoggingEvent, formattedMessage: Any?) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val timeStr =
                "${now.year}-${now.monthNumber}-${now.dayOfMonth} ${now.hour}:${now.minute}:${now.second},${now.nanosecond / 1000000}"
            if (loggingEvent.level == Level.ERROR) {
                fprintf(stderr, "$timeStr: $formattedMessage\n")
            } else {
                println("$timeStr: $formattedMessage")
            }
        }
    }
}
