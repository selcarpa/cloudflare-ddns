package model.config

import exception.CFDdnsException
import filePath
import genFile
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.mamoe.yamlkt.Yaml
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import utils.readFile
import utils.writeFile

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    explicitNulls = false
    allowTrailingComma = true
}

private val toml = Toml {
    ignoreUnknownKeys = true
    explicitNulls = false
}
private val yaml = Yaml {

}

object Config {
    val Configuration: ConfigurationSetting
        get() {
            if (Config::dConfiguration.isInitialized) {
                return dConfiguration
            }
            throw CFDdnsException("Configuration not initialized, please use '-c' to specify configuration file or '-gen' to generate one")
        }
    private lateinit var dConfiguration: ConfigurationSetting

    fun initConfiguration(configurationUrl: String) {
        if (Config::dConfiguration.isInitialized) {
            throw CFDdnsException("Configuration already initialized by '-gen'")
        }
        val content = readFile(configurationUrl.toPath())
        dConfiguration =
            when {
                configurationUrl.endsWith("json") || configurationUrl.endsWith("json5") -> {
                    json.decodeFromString<ConfigurationSetting>(content)
                }

                configurationUrl.endsWith("toml") -> {
                    toml.decodeFromString(ConfigurationSetting.serializer(), content)
                }

                configurationUrl.endsWith("yml") || configurationUrl.endsWith("yaml") -> {
                    yaml.decodeFromString(ConfigurationSetting.serializer(), content)
                }

                else -> {
                    throw CFDdnsException("not supported file type")
                }
            }

        dConfiguration.propertiesCover()
    }

    private fun propertiesCover(domain: Domain, common: Properties): Properties {
        val domainProperties = domain.properties
        val proxied = domainProperties?.proxied ?: common.proxied ?: false
        return Properties(
            zoneId = domainProperties?.zoneId ?: common.zoneId
            ?: throw IllegalArgumentException("no zoneId specified for ${domain.name}"),
            authKey = domainProperties?.authKey ?: common.authKey
            ?: throw IllegalArgumentException("no authKey specified for ${domain.name}"),
            checkUrlV4 = domainProperties?.checkUrlV4 ?: common.checkUrlV4 ?: "https://api4.ipify.org?format=text",
            checkUrlV6 = domainProperties?.checkUrlV6 ?: common.checkUrlV6 ?: "https://api6.ipify.org?format=text",
            v4 = domainProperties?.v4 ?: common.v4 ?: true,
            v6 = domainProperties?.v6 ?: common.v6 ?: false,
            ttl = domainProperties?.ttl ?: common.ttl ?: 300,
            autoPurge = domainProperties?.autoPurge ?: common.autoPurge ?: false,
            proxied = proxied,
            ttlCheck = run {
                val ttlCheck = domainProperties?.ttlCheck ?: common.ttlCheck ?: false
                if (ttlCheck && proxied) {
                    logger.warn { "ttlCheck is not supported when proxied is true, set ttlCheck to false for ${domain.name}" }
                    false
                } else {
                    ttlCheck
                }
            },
            comment = domainProperties?.comment ?: common.comment,
            reInit = domainProperties?.reInit ?: common.reInit ?: run {//
                val i = 300 / (domainProperties?.ttl ?: common.ttl ?: 300)
                if (i == 0) {
                    1
                } else {
                    i
                }
            }
        )
    }

    fun genConfiguration(domain: String?, zoneId: String?, authKey: String?, v4: Boolean?, v6: Boolean?) {
        if (Config::dConfiguration.isInitialized) {
            logger.info { "Configuration file is specified, cf-ddns will ignore '-gen'" }
            return
        }
        val configurationSetting = ConfigurationSetting(
            listOf(Domain(domain!!, null)), Properties(
                zoneId = zoneId!!,
                authKey = authKey!!,
                null,
                null,
                v4 = v4 ?: true,
                v6 = v6 ?: false,
                null,
                autoPurge = false,
                false,
                null,
                ttlCheck = true,
                reInit = 1
            )
        )
        if (genFile) {
            val configPath = filePath.toPath()
            writeFile(
                configPath, json.encodeToString(ConfigurationSetting.serializer(), configurationSetting)
            )
            logger.info { "configuration file generated at $filePath, next time you can use \"-c=$filePath\" to specify this configuration file" }
        }
        this.dConfiguration = configurationSetting.also {
            it.propertiesCover()
        }
    }

    private fun ConfigurationSetting.propertiesCover() {
        this.domains.forEach {
            if (it.properties == null) {
                it.properties = this.common
            }
            //cover again to avoid some empty value in common
            it.properties = propertiesCover(it, this.common)
            //override as lower case domain
            it.name = it.name.lowercase()
        }
    }
}

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
)

@Serializable
data class ConfigurationSetting(
    var domains: List<Domain> = emptyList(), var common: Properties
)

@Serializable
data class Domain(
    var name: String, var properties: Properties?
)
