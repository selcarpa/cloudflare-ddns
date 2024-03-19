import io.github.oshai.kotlinlogging.FormattingAppender
import io.github.oshai.kotlinlogging.KLoggingEvent
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
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

@OptIn(FormatStringsInDatetimeFormats::class)
val dateTimeFormat = LocalDateTime.Format {
    byUnicodePattern("yyyy-MM-dd HH:mm:ss SSS")
}

@OptIn(ExperimentalForeignApi::class)
actual fun logAppenderSet() {
    KotlinLoggingConfiguration.appender = object : FormattingAppender() {
        override fun logFormattedMessage(loggingEvent: KLoggingEvent, formattedMessage: Any?) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val timeStr = dateTimeFormat.format(now)
            if (loggingEvent.level == Level.ERROR) {
                fprintf(stderr, "$timeStr: $formattedMessage\n")
            } else {
                println("$timeStr: $formattedMessage")
            }
        }
    }
}
