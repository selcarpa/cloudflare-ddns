package model.config

import kotlinx.serialization.json.Json
import net.mamoe.yamlkt.Yaml
import net.peanuuutz.tomlkt.Toml

actual fun loadFromResource(
    json: Json,
    toml: Toml,
    yaml: Yaml
): ConfigurationSetting {
    TODO("Kotlin native not support read resource file, please use -c=xxx to specify configuration file")
}
