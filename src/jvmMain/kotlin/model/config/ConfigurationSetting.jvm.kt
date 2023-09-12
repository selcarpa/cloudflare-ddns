package model.config

import kotlinx.serialization.json.Json
import net.mamoe.yamlkt.Yaml
import net.peanuuutz.tomlkt.Toml

actual fun loadFromResource(
    json: Json, toml: Toml, yaml: Yaml
): ConfigurationSetting {
    return yaml.decodeFromString(
        ConfigurationSetting.serializer(),
        ConfigurationSetting::class.java.getResource("/default.yml")?.readText()!!
    )
}
