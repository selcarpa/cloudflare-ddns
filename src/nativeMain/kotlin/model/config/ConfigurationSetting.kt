package model.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.peanuuutz.tomlkt.Toml
import okio.Path.Companion.toPath
import utils.readFile
import kotlin.system.exitProcess


@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

private val toml = Toml {
    ignoreUnknownKeys = true
}

object Config {
    var ConfigurationUrl: String? = null
    val Configuration: ConfigurationSetting by lazy { initConfiguration() }

    private fun initConfiguration(): ConfigurationSetting {
        val configurationSetting = if (ConfigurationUrl.orEmpty().isEmpty()) {
            TODO("Kotlin native not support read resource file, please use -c=xxx to specify configuration file")
        } else {
            try {
                val content = readFile(ConfigurationUrl!!.toPath())
                if (ConfigurationUrl!!.endsWith("json") || ConfigurationUrl!!.endsWith("json5")) {
                    json.decodeFromString<ConfigurationSetting>(content)
                } else if (ConfigurationUrl!!.endsWith("toml")) {
                    toml.decodeFromString(ConfigurationSetting.serializer(), content)
                } else {
                    throw IllegalArgumentException("not supported file type")
                }
            } catch (ex: Exception) {
                print("load configuration failed: ${ex.message}")
                ex.printStackTrace()
                exitProcess(1)
            }
        }

        configurationSetting.domains.forEach {
            if (it.properties == null) {
                it.properties = configurationSetting.common
            } else {
                it.properties = propertiesCover(it.properties, configurationSetting.common)
            }
        }

        return configurationSetting
    }

    private fun propertiesCover(properties: Properties?, common: Properties): Properties {
        return Properties(//after serialization, properties will be null in some case
            zoneId = properties?.zoneId ?: common.zoneId!!,
            authKey = properties?.authKey ?: common.authKey!!,
            checkUrlv4 = properties?.checkUrlv4 ?: common.checkUrlv4!!,
            checkUrlv6 = properties?.checkUrlv6 ?: common.checkUrlv6!!,
            v4 = properties?.v4 ?: common.v4!!,
            v6 = properties?.v6 ?: common.v6!!,
            ttl = properties?.ttl ?: common.ttl!!,
            autoPurge = properties?.autoPurge ?: common.autoPurge
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
    var ttl: Int?,
    var autoPurge: Boolean? = false
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


