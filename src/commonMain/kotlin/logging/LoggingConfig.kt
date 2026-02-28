package logging

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.Level
import io.github.oshai.kotlinlogging.KLogger

/**
 * Logging configuration and utilities
 */
object LoggingConfig {
    
    private var isConfigured = false
    private var debugEnabled = false
    
    /**
     * Configure logging based on application settings
     */
    fun configure(debug: Boolean = false) {
        if (isConfigured) {
            return
        }
        
        debugEnabled = debug
        isConfigured = true
        
        // Configure root logger level
        val rootLogger = KotlinLogging.logger {}
        rootLogger.let {
            // Note: Actual level configuration depends on kotlin-logging implementation
        }
        
        if (debug) {
            println("[Logging] Debug mode enabled")
        }
    }
    
    /**
     * Check if debug logging is enabled
     */
    fun isDebugEnabled(): Boolean = debugEnabled
    
    /**
     * Create a logger with standard configuration
     */
    fun logger(name: String): KLogger {
        return KotlinLogging.logger(name)
    }
    
    /**
     * Create a logger for a class
     */
    fun <T : Any> logger(clazz: Class<T>): KLogger {
        return KotlinLogging.logger(clazz)
    }
}

/**
 * Log sanitization utilities
 */
object LogSanitizer {
    
    private val SENSITIVE_PATTERNS = listOf(
        Regex("""authKey["']?\s*[:=]\s*["']?([^"'\s,}]+)"""),
        Regex("""Authorization["']?\s*[:=]\s*["']?([^"'\s,}]+)"""),
        Regex("""api[_-]?key["']?\s*[:=]\s*["']?([^"'\s,}]+)"""),
        Regex("""token["']?\s*[:=]\s*["']?([^"'\s,}]+)""")
    )
    
    private const val REDACTED = "[REDACTED]"
    
    /**
     * Sanitize log message to remove sensitive information
     */
    fun sanitize(message: String): String {
        var sanitized = message
        SENSITIVE_PATTERNS.forEach { pattern ->
            sanitized = pattern.replace(sanitized) { match ->
                match.groupValues[0].replace(match.groupValues[1], REDACTED)
            }
        }
        return sanitized
    }
    
    /**
     * Sanitize headers map
     */
    fun sanitizeHeaders(headers: Map<String, String>): Map<String, String> {
        val sensitiveHeaders = setOf(
            "authorization",
            "x-api-key",
            "x-auth-token",
            "cookie",
            "set-cookie"
        )
        
        return headers.mapValues { (key, value) ->
            if (key.lowercase() in sensitiveHeaders) {
                REDACTED
            } else {
                value
            }
        }
    }
}

/**
 * Structured logging helpers
 */
object LogHelpers {
    
    /**
     * Format a duration for logging
     */
    fun formatDuration(ms: Long): String {
        return when {
            ms < 1000 -> "${ms}ms"
            ms < 60000 -> "${ms / 1000.0}s"
            else -> "${ms / 60000.0}m"
        }
    }
    
    /**
     * Format a size in bytes for logging
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024.0}KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024.0 * 1024)}MB"
            else -> "${bytes / (1024.0 * 1024 * 1024)}GB"
        }
    }
    
    /**
     * Create a task timing log message
     */
    fun taskTiming(taskName: String, durationMs: Long, success: Boolean): String {
        val status = if (success) "completed" else "failed"
        return "$taskName $status in ${formatDuration(durationMs)}"
    }
}
