package model.config

import exception.CFDdnsException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import utils.readFile
import utils.writeFile
import Constants
import AppContext

private val logger = KotlinLogging.logger {}

/**
 * JSON configuration with lenient parsing
 */
@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    explicitNulls = false
    allowTrailingComma = true
}

/**
 * TOML configuration parser
 */
private val toml = Toml {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Configuration management object
 */
object Config {
    
    /**
     * Get initialized configuration
     * @throws CFDdnsException if configuration not initialized
     */
    val Configuration: ConfigurationSetting
        get() {
            if (!Config::dConfiguration.isInitialized) {
                throw CFDdnsException(Constants.ErrorMessages.CONFIG_NOT_INITIALIZED)
            }
            return dConfiguration
        }
    
    private lateinit var dConfiguration: ConfigurationSetting

    /**
     * Initialize configuration from file
     * @param configurationUrl path to configuration file (json, json5, or toml)
     * @throws CFDdnsException if file not found or invalid format
     */
    fun initConfiguration(configurationUrl: String) {
        if (Config::dConfiguration.isInitialized) {
            throw CFDdnsException(Constants.ErrorMessages.CONFIG_ALREADY_INITIALIZED)
        }
        
        val content = try {
            readFile(configurationUrl.toPath())
        } catch (e: Exception) {
            throw CFDdnsException("Failed to read configuration file: ${e.message}")
        }
        
        dConfiguration = when {
            configurationUrl.endsWith(Constants.FileExtensions.JSON) || 
            configurationUrl.endsWith(Constants.FileExtensions.JSON5) -> {
                json.decodeFromString<ConfigurationSetting>(content)
            }
            configurationUrl.endsWith(Constants.FileExtensions.TOML) -> {
                toml.decodeFromString(ConfigurationSetting.serializer(), content)
            }
            else -> {
                throw CFDdnsException(Constants.ErrorMessages.UNSUPPORTED_FILE_TYPE)
            }
        }

        dConfiguration.validateAndApplyDefaults()
    }

    /**
     * Merge domain-specific properties with common properties
     */
    private fun mergeProperties(domain: Domain, common: Properties): Properties {
        val domainProps = domain.properties
        val proxied = domainProps?.proxied ?: common.proxied ?: Constants.Defaults.PROXIED
        
        return Properties(
            zoneId = domainProps?.zoneId ?: common.zoneId
                ?: throw IllegalArgumentException(Constants.ErrorMessages.MISSING_ZONE_ID.replace("{domain}", domain.name)),
            authKey = domainProps?.authKey ?: common.authKey
                ?: throw IllegalArgumentException(Constants.ErrorMessages.MISSING_AUTH_KEY.replace("{domain}", domain.name)),
            checkUrlV4 = domainProps?.checkUrlV4 ?: common.checkUrlV4 ?: Constants.DEFAULT_IP_CHECK_URL_V4,
            checkUrlV6 = domainProps?.checkUrlV6 ?: common.checkUrlV6 ?: Constants.DEFAULT_IP_CHECK_URL_V6,
            v4 = domainProps?.v4 ?: common.v4 ?: Constants.Defaults.IPV4_ENABLED,
            v6 = domainProps?.v6 ?: common.v6 ?: Constants.Defaults.IPV6_ENABLED,
            ttl = normalizeTtl(domainProps?.ttl ?: common.ttl ?: Constants.Defaults.TTL_SECONDS),
            autoPurge = domainProps?.autoPurge ?: common.autoPurge ?: Constants.Defaults.AUTO_PURGE,
            proxied = proxied,
            ttlCheck = calculateTtlCheck(domainProps, common, proxied, domain.name),
            comment = domainProps?.comment ?: common.comment,
            reInit = calculateReInitInterval(domainProps, common)
        )
    }
    
    /**
     * Normalize TTL value to valid range
     */
    private fun normalizeTtl(ttl: Int): Int {
        return when {
            ttl <= 0 -> Constants.Defaults.TTL_SECONDS
            ttl < 30 -> 30  // Cloudflare minimum TTL
            ttl > 7200 -> 7200  // Cloudflare maximum TTL for proxied
            else -> ttl
        }
    }
    
    /**
     * Calculate TTL check flag with validation
     */
    private fun calculateTtlCheck(domainProps: Properties?, common: Properties, proxied: Boolean, domainName: String): Boolean {
        val ttlCheck = domainProps?.ttlCheck ?: common.ttlCheck ?: Constants.Defaults.TTL_CHECK
        return if (ttlCheck && proxied) {
            logger.warn { 
                "TTL check is not supported when proxied is enabled. " +
                "Disabling ttlCheck for domain: $domainName"
            }
            false
        } else {
            ttlCheck
        }
    }
    
    /**
     * Calculate re-initialization interval
     */
    private fun calculateReInitInterval(domainProps: Properties?, common: Properties): Int {
        return domainProps?.reInit ?: common.reInit ?: run {
            val ttl = domainProps?.ttl ?: common.ttl ?: Constants.Defaults.TTL_SECONDS
            val interval = 300 / ttl
            if (interval == 0) 1 else interval
        }
    }

    /**
     * Generate configuration programmatically
     */
    fun genConfiguration(
        domain: String, 
        zoneId: String, 
        authKey: String, 
        v4: Boolean?, 
        v6: Boolean?
    ) {
        if (Config::dConfiguration.isInitialized) {
            logger.info { "Configuration already specified, ignoring -gen flag" }
            return
        }
        
        val configurationSetting = ConfigurationSetting(
            domains = listOf(Domain(domain, null)), 
            common = Properties(
                zoneId = zoneId,
                authKey = authKey,
                checkUrlV4 = null,
                checkUrlV6 = null,
                v4 = v4 ?: Constants.Defaults.IPV4_ENABLED,
                v6 = v6 ?: Constants.Defaults.IPV6_ENABLED,
                ttl = Constants.Defaults.TTL_SECONDS,
                autoPurge = Constants.Defaults.AUTO_PURGE,
                proxied = Constants.Defaults.PROXIED,
                comment = null,
                ttlCheck = true,
                reInit = 1
            )
        )
        
        if (AppContext.outputPath != null) {
            val configPath = AppContext.outputPath!!.toPath()
            writeFile(
                configPath, 
                json.encodeToString(ConfigurationSetting.serializer(), configurationSetting)
            )
            logger.info { 
                "Configuration generated at: ${AppContext.outputPath}. " +
                "Use '-c=${AppContext.outputPath}' to specify this file next time."
            }
        }
        
        this.dConfiguration = configurationSetting.also {
            it.validateAndApplyDefaults()
        }
    }

    /**
     * Validate configuration and apply defaults to all domains
     */
    private fun ConfigurationSetting.validateAndApplyDefaults() {
        require(domains.isNotEmpty()) { "At least one domain must be configured" }
        
        this.domains.forEach { domain ->
            // Validate domain name format
            if (!isValidDomainName(domain.name)) {
                logger.warn { "Domain name '${domain.name}' may not be valid" }
            }
            
            // Apply properties inheritance
            if (domain.properties == null) {
                domain.properties = this.common
            }
            domain.properties = mergeProperties(domain, this.common)
            
            // Normalize domain name to lowercase
            domain.name = domain.name.lowercase()
        }
        
        logger.info { "Configuration loaded for ${domains.size} domain(s)" }
    }
    
    /**
     * Basic domain name validation
     */
    private fun isValidDomainName(name: String): Boolean {
        if (name.isBlank() || name.length > 253) return false
        if (name.startsWith(".") || name.endsWith(".")) return false
        if (name.contains("..")) return false
        return true
    }
}

/**
 * Domain properties configuration
 */
@Serializable
data class Properties(
    val zoneId: String?,
    val authKey: String?,
    val checkUrlV4: String?,
    val checkUrlV6: String?,
    val v4: Boolean?,
    val v6: Boolean?,
    val ttl: Int?,
    val autoPurge: Boolean?,
    val proxied: Boolean?,
    var comment: String?,
    val ttlCheck: Boolean?,
    val reInit: Int?
) {
    init {
        require(zoneId.isNullOrBlank() || zoneId.length <= 32) { 
            "Invalid zoneId format" 
        }
        require(ttl == null || (ttl in 30..7200)) { 
            "TTL must be between 30 and 7200 seconds" 
        }
        require(reInit == null || reInit >= 0) { 
            "reInit must be non-negative" 
        }
    }
}

/**
 * Root configuration setting
 */
@Serializable
data class ConfigurationSetting(
    var domains: List<Domain> = emptyList(), 
    var common: Properties
)

/**
 * Domain configuration
 */
@Serializable
data class Domain(
    var name: String, 
    var properties: Properties?
)
