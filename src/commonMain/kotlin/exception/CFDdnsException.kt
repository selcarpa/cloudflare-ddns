package exception

/**
 * Base exception for Cloudflare-DDNS application
 */
open class CFDdnsException(
    message: String,
    val errorCode: ErrorCode = ErrorCode.UNKNOWN
) : RuntimeException(message) {
    
    /**
     * Create exception with cause
     */
    constructor(message: String, cause: Throwable, errorCode: ErrorCode = ErrorCode.UNKNOWN) 
        : this("$message: ${cause.message}", errorCode)
}

/**
 * Configuration-related exceptions
 */
class ConfigurationException(
    message: String,
    errorCode: ErrorCode = ErrorCode.CONFIG_ERROR
) : CFDdnsException(message, errorCode)

/**
 * Cloudflare API-related exceptions
 */
class CloudflareApiException(
    message: String,
    val httpCode: Int? = null,
    val apiErrorCode: Int? = null
) : CFDdnsException(message, ErrorCode.CLOUDFLARE_API_ERROR)

/**
 * Network-related exceptions
 */
class NetworkException(
    message: String,
    cause: Throwable? = null
) : CFDdnsException(message, cause ?: UnknownError(), ErrorCode.NETWORK_ERROR)

/**
 * IP detection exceptions
 */
class IpDetectionException(
    message: String = "Failed to detect public IP address"
) : CFDdnsException(message, ErrorCode.IP_DETECTION_ERROR)

/**
 * DNS record operation exceptions
 */
class DnsRecordException(
    message: String,
    val operation: DnsOperation = DnsOperation.UNKNOWN
) : CFDdnsException(message, ErrorCode.DNS_RECORD_ERROR)

/**
 * Error codes for better error handling
 */
enum class ErrorCode(val code: Int, val description: String) {
    UNKNOWN(0, "Unknown error"),
    CONFIG_ERROR(1001, "Configuration error"),
    CLOUDFLARE_API_ERROR(2001, "Cloudflare API error"),
    NETWORK_ERROR(3001, "Network error"),
    IP_DETECTION_ERROR(3002, "IP detection error"),
    DNS_RECORD_ERROR(4001, "DNS record operation error"),
    AUTH_ERROR(4002, "Authentication error"),
    RATE_LIMIT_ERROR(4003, "Rate limit exceeded"),
    VALIDATION_ERROR(5001, "Validation error");
    
    companion object {
        fun fromHttpCode(httpCode: Int): ErrorCode {
            return when (httpCode) {
                400, 401, 403 -> AUTH_ERROR
                429 -> RATE_LIMIT_ERROR
                in 500..599 -> CLOUDFLARE_API_ERROR
                else -> UNKNOWN
            }
        }
    }
}

/**
 * DNS operation types
 */
enum class DnsOperation {
    CREATE,
    UPDATE,
    DELETE,
    READ,
    UNKNOWN
}
