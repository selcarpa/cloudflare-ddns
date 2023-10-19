package model.config

import exception.CFDdnsException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.mamoe.yamlkt.Yaml
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import utils.readFile

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

private val toml = Toml {
    ignoreUnknownKeys = true
    explicitNulls = false
}
private val yaml = Yaml {

}

object Config {
    var ConfigurationUrl: String? = null
    val Configuration: ConfigurationSetting by lazy { initConfiguration() }

    private fun initConfiguration(): ConfigurationSetting {
        val configurationSetting = if (ConfigurationUrl.orEmpty().isEmpty()) {
            printGuide()
        } else {
            val content = readFile(ConfigurationUrl!!.toPath())
            when {
                ConfigurationUrl!!.endsWith("json") || ConfigurationUrl!!.endsWith("json5") -> {
                    json.decodeFromString<ConfigurationSetting>(content)
                }

                ConfigurationUrl!!.endsWith("toml") -> {
                    toml.decodeFromString(ConfigurationSetting.serializer(), content)
                }

                ConfigurationUrl!!.endsWith("yml") || ConfigurationUrl!!.endsWith("yaml") -> {
                    yaml.decodeFromString(ConfigurationSetting.serializer(), content)
                }

                else -> {
                    throw IllegalArgumentException("not supported file type")
                }
            }

        }

        configurationSetting.domains.forEach {
            if (it.properties == null) {
                it.properties = configurationSetting.common
            }
            //cover again to avoid some empty value in common
            it.properties = propertiesCover(it, configurationSetting.common)
        }

        return configurationSetting
    }

    private fun printGuide(): ConfigurationSetting {
        logger.info { "no configuration file specified, please use \"-c=configfile\" to specify a configuration file" }
        throw CFDdnsException()
    }


    private fun propertiesCover(domain: Domain, common: Properties): Properties {
        val properties = domain.properties
        return Properties(
            zoneId = properties?.zoneId ?: common.zoneId
            ?: throw IllegalArgumentException("no zoneId specified for ${domain.name}"),
            authKey = properties?.authKey ?: common.authKey
            ?: throw IllegalArgumentException("no authKey specified for ${domain.name}"),
            checkUrlV4 = properties?.checkUrlV4 ?: common.checkUrlV4 ?: "https://api4.ipify.org?format=text",
            checkUrlV6 = properties?.checkUrlV6 ?: common.checkUrlV6 ?: "https://api6.ipify.org?format=text",
            v4 = properties?.v4 ?: common.v4 ?: true,
            v6 = properties?.v6 ?: common.v6 ?: false,
            ttl = properties?.ttl ?: common.ttl ?: 300,
            autoPurge = properties?.autoPurge ?: common.autoPurge ?: false,
            proxied = properties?.proxied ?: common.proxied ?: false
        )
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
    val proxied: Boolean?
)

@Serializable
data class ConfigurationSetting(
    var domains: List<Domain> = emptyList(), var common: Properties
)

@Serializable
data class Domain(
    val name: String,
    var properties: Properties?,
)


