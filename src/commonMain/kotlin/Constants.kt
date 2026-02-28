/**
 * Application constants
 */
object Constants {
    /**
     * Application info
     */
    const val APP_NAME = "Cloudflare-DDNS"
    const val VERSION = "1.54-SNAPSHOT"
    
    /**
     * Default IP detection URLs
     */
    const val DEFAULT_IP_CHECK_URL_V4 = "https://api4.ipify.org?format=text"
    const val DEFAULT_IP_CHECK_URL_V6 = "https://api6.ipify.org?format=text"
    
    /**
     * Cloudflare API
     */
    const val CLOUDFLARE_API_BASE = "https://api.cloudflare.com/client/v4"
    const val CLOUDFLARE_API_DNS_RECORDS = "/zones/{zoneId}/dns_records"
    
    /**
     * DNS record types
     */
    object DnsRecordType {
        const val A = "A"
        const val AAAA = "AAAA"
    }
    
    /**
     * Default values
     */
    object Defaults {
        const val TTL_SECONDS = 300
        const val AUTO_PURGE = false
        const val PROXIED = false
        const val TTL_CHECK = false
        const val IPV4_ENABLED = true
        const val IPV6_ENABLED = false
        const val RE_INIT_INTERVAL = 0 // Disabled by default
    }
    
    /**
     * File extensions
     */
    object FileExtensions {
        const val JSON = "json"
        const val JSON5 = "json5"
        const val TOML = "toml"
    }
    
    /**
     * Logging
     */
    object Logging {
        const val STARTUP_MESSAGE = "やらなくて後悔するよりも、やって後悔したほうがいいっていうよね？"
        const val DEBUG_MODE_MESSAGE = "Debug mode enabled"
    }
    
    /**
     * Error messages
     */
    object ErrorMessages {
        const val CONFIG_NOT_INITIALIZED = "Configuration not initialized. Use '-c' to specify config file or '-gen' to generate one"
        const val CONFIG_ALREADY_INITIALIZED = "Configuration already initialized"
        const val UNSUPPORTED_FILE_TYPE = "Unsupported configuration file type. Supported: json, json5, toml"
        const val MISSING_ZONE_ID = "No zoneId specified for {domain}"
        const val MISSING_AUTH_KEY = "No authKey specified for {domain}"
        const val GEN_MISSING_PARAMS = "zoneId, authKey, and domain are required for configuration generation"
    }
}
