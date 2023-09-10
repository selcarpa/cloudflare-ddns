package model.config

import kotlinx.serialization.json.Json
import net.mamoe.yamlkt.Yaml
import net.peanuuutz.tomlkt.Toml

actual fun loadFromResource(
    json: Json,
    toml: Toml,
    yaml: Yaml
): ConfigurationSetting {
    val content = ConfigurationSetting::class.java.getResource("/default.yaml")?.readText()
    return yaml.decodeFromString(ConfigurationSetting.serializer(), content!!)
}
