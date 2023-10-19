import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.github.oshai.kotlinlogging.Level
import kotlin.system.exitProcess

actual fun debugLogSet() {
    KotlinLoggingConfiguration.logLevel = Level.ERROR
}

actual fun exitGracefully() {
    exitProcess(1)
}
