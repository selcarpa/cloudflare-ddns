package model.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.mamoe.yamlkt.Yaml
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import utils.readFile


@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

private val toml = Toml {
    ignoreUnknownKeys = true
}
private val yaml = Yaml {

}

expect fun loadFromResource(json: Json, toml: Toml, yaml: Yaml): ConfigurationSetting

object Config {
    var ConfigurationUrl: String? = null
    val Configuration: ConfigurationSetting by lazy { initConfiguration() }

    private fun initConfiguration(): ConfigurationSetting {
        val configurationSetting = if (ConfigurationUrl.orEmpty().isEmpty()) {
            loadFromResource(json, toml, yaml)
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
            } else {
                it.properties = propertiesCover(it, configurationSetting.common)
            }
        }

        return configurationSetting
    }


    private fun propertiesCover(domain: Domain, common: Properties): Properties {
        val properties = domain.properties
        return Properties(
            zoneId = properties?.zoneId ?: common.zoneId
            ?: throw IllegalArgumentException("no zoneId specified for ${domain.name}"),
            authKey = properties?.authKey ?: common.authKey
            ?: throw IllegalArgumentException("no authKey specified for ${domain.name}"),
            checkUrlv4 = properties?.checkUrlv4 ?: common.checkUrlv4 ?: "https://api4.ipify.org?format=text",
            checkUrlv6 = properties?.checkUrlv6 ?: common.checkUrlv6 ?: "https://api6.ipify.org?format=text",
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
    val checkUrlv4: String?,
    val checkUrlv6: String?,
    val v4: Boolean?,
    val v6: Boolean?,
    val ttl: Int?,
    val autoPurge: Boolean?,
    val proxied: Boolean?
)

@Serializable
data class ConfigurationSetting(
    var domains: List<Domain> = emptyList(),
    var common: Properties
)

@Serializable
data class Domain(
    val name: String,
    var proxied: Boolean = false,
    var properties: Properties?,
)


