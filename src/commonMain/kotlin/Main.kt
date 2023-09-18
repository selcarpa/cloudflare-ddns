import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.config.Config.Configuration
import model.config.Config.ConfigurationUrl
import model.config.Domain
import model.request.CloudflareBody
import model.request.DnsRecord
import model.request.UpdateDnsRecordRequest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

expect fun info(message: () -> Any?)

expect fun warn(message: () -> Any?)

expect fun error(e: Exception)
expect fun error(message: () -> Any?)


expect fun debug(message: () -> Any?)


private val json = Json {
    ignoreUnknownKeys = true
}

private var debug = false


private val client by lazy {
    HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        if (debug) {
            this.initLogging()
        }
    }
}

expect fun <T : HttpClientEngineConfig> HttpClientConfig<T>.initLogging()

expect fun debugLogSet()


fun main(args: Array<String>) = runBlocking {
    args.forEach {
        println(it)
        if (it.startsWith("-c=")) {
            ConfigurationUrl = it.replace("-c=", "")
        }
        if (it == "--debug") {
            debug = true
            debugLogSet()
        }
    }

    debug { "debug-mode online" }

    info { "やらなくて後悔するよりも、やって後悔したほうがいいっていうよね？" }

    val ddnsItems = Configuration.domains.map { it.toDDnsItems() }.flatten().toList()

    ddnsItems.groupBy {
        it.domain.properties!!.ttl
    }.forEach { (ttl, d) ->
        d.filter { it.domain.properties?.v4 ?: false }
            .groupBy {
                it.domain.properties!!.checkUrlV4
            }.forEach {
                launchTask(ttl, it)
            }
        d.filter { it.domain.properties?.v6 ?: false }
            .groupBy {
                it.domain.properties!!.checkUrlV6
            }.forEach {
                launchTask(ttl, it)
            }
    }

    Configuration.domains.filter { it.properties?.autoPurge ?: false }
        .filter { it.properties!!.v4!! || it.properties!!.v6!! }.forEach {
            launch(Dispatchers.Default) {
                if (it.properties?.v4 != true) {
                    launch(Dispatchers.Default) {
                        delayCall(it.properties!!.ttl!!.seconds) {
                            it.toV4DdnsItems().purge()
                        }
                    }

                } else if (it.properties?.v6 != true) {
                    launch(Dispatchers.Default) {
                        delayCall(it.properties!!.ttl!!.seconds) {
                            it.toV6DdnsItems().purge()
                        }
                    }
                }
            }
        }

}

fun DdnsItem.purge() = runBlocking {
    while (true) {
        if (!this@purge.inited && !this@purge.init()) {
            error { "purge ${this@purge.domain.name} ${this@purge.type.name} failed, try after ${this@purge.domain.properties!!.ttl}" }
        }

        if (!this@purge.exists) {
            info { "no exists record for ${this@purge.domain.name} ${this@purge.type.name}, purge task stopped" }
            break
        }

        if (purge(this@purge)) {
            break
        }
        delay(this@purge.domain.properties!!.ttl!!.seconds)
    }
}

suspend fun purge(ddnsItem: DdnsItem): Boolean {
    val authHeader = ddnsItem.authHeader()
    val httpResponse = client.delete(
        "https://api.cloudflare.com/client/v4/zones/${ddnsItem.domain.properties!!.zoneId}/purge_cache"
    ) {
        headers {
            authHeader.forEach {
                append(it.key, it.value)
            }
        }

    }
    val cloudflareBody = httpResponse.body<CloudflareBody<Any>>()
    if (!cloudflareBody.success) {
        error { cloudflareBody.errors }
        return false
    }
    info { "purge ${ddnsItem.domain.name} ${ddnsItem.type.name} successful" }
    return true
}

private fun CoroutineScope.launchTask(
    ttl: Int?,
    it: Map.Entry<String?, List<DdnsItem>>
) {
    launch(Dispatchers.Default) {
        delayCall(ttl!!.seconds) {
            ddns(it.value) {
                getIp(it.key!!)
            }
        }
    }
}

fun delayCall(duration: Duration, exec: () -> Unit) = runBlocking {
    while (true) {
        //https://kotlinlang.org/docs/coroutines-and-channels.html#using-the-outer-scope-s-context
        //https://kotlinlang.org/docs/exception-handling.html
        launch(Dispatchers.Default) {
            try {
                exec()
            } catch (e: Exception) {
                error(e)
            }
        }
        delay(duration)
    }
}

private fun ddns(ddnsItems: List<DdnsItem>, ipSupplier: suspend () -> String) = runBlocking {
    debug { "start ${ddnsItems[0].type} ddns task for ${(ddnsItems.joinToString(",") { it.domain.name })}" }
    val ip = ipSupplier()

    ddnsItems.forEach {
        it.run(ip)
    }
}

suspend fun getIp(checkApi: String): String {
    val response = client.get(checkApi)
    debug { response }
    val ip = response.bodyAsText()
    debug { "get current ip: $ip from $checkApi" }
    return ip
}

private fun Domain.toV4DdnsItems(): DdnsItem {
    return DdnsItem(
        domain = this, type = TYPE.A
    )
}

private fun Domain.toV6DdnsItems(): DdnsItem {
    return DdnsItem(
        domain = this, type = TYPE.AAAA
    )
}

private fun Domain.toDDnsItems(): List<DdnsItem> {
    return listOf(
        if (this.properties!!.v4!!) {
            this.toV4DdnsItems()
        } else {
            null
        },
        if (this.properties!!.v6!!) {
            this.toV6DdnsItems()
        } else {
            null
        },
    ).filterNotNull()
}

private fun DdnsItem.run(ip: String) = runBlocking {
    if (!this@run.inited && !this@run.init()) {
        return@runBlocking
    }

    if (this@run.exists) {
        if (ip != this@run.content // if ip on cloudflare not equals to current ip
            || this@run.proxied != this@run.domain.properties!!.proxied  // if proxied on cloudflare not equals to config
            || this@run.ttl != this@run.domain.properties!!.ttl // if ttl on cloudflare not equals to config
        ) {
            info { "update ${this@run.domain.name} to ${this@run.type.name} $ip" }
            updateDns(ip, this@run, true)
            return@runBlocking
        }

        info {
            "checked: ${this@run.type} ${this@run.domain.name} already been resolve to $ip ${
                if (this@run.domain.properties!!.proxied == true) "proxied" else {
                    "not proxied"
                }
            }"
        }
        return@runBlocking
    }

    info { "create ${this@run.domain.name} with ${this@run.type.name} $ip" }
    updateDns(ip, this@run)
}

suspend fun updateDns(ip: String, ddnsItem: DdnsItem, update: Boolean = false) {
    val authHeader = ddnsItem.authHeader()
    val httpResponse = client.request(
        if (update) {
            "https://api.cloudflare.com/client/v4/zones/${ddnsItem.domain.properties!!.zoneId}/dns_records/${ddnsItem.id}/"
        } else {
            "https://api.cloudflare.com/client/v4/zones/${ddnsItem.domain.properties!!.zoneId}/dns_records/"
        }
    ) {
        setBody(
            json.encodeToString(
                if (update) {
                    UpdateDnsRecordRequest(
                        type = ddnsItem.type.name,
                        name = ddnsItem.domain.name,
                        content = ip,
                        ttl = ddnsItem.ttl!!,
                        proxied = ddnsItem.proxied!!,
                        tags = emptyList()
                    )
                } else {
                    UpdateDnsRecordRequest(
                        type = ddnsItem.type.name,
                        name = ddnsItem.domain.name,
                        content = ip,
                        ttl = ddnsItem.domain.properties!!.ttl!!,
                        proxied = ddnsItem.domain.properties!!.proxied!!,
                        tags = emptyList()
                    )
                }
            )
        )
        headers {
            authHeader.forEach {
                append(it.key, it.value)
            }
        }
        method = if (update) {
            HttpMethod.Put
        } else {
            HttpMethod.Post
        }
    }
    val cloudflareBody = httpResponse.body<CloudflareBody<DnsRecord>>()
    if (!cloudflareBody.success) {
        error { cloudflareBody.errors }
    }
    if (update) {
        info { "updated ${ddnsItem.domain.name} to $ip successful" }
    } else {
        info { "created ${ddnsItem.domain.name} with $ip successful" }
    }
    ddnsItem.init(cloudflareBody.result!!)
}

private suspend fun DdnsItem.init(): Boolean {
    val authHeader = this.authHeader()
    val dnsRecords =
        client.get("https://api.cloudflare.com/client/v4/zones/${this.domain.properties!!.zoneId}/dns_records") {
            headers {
                authHeader.forEach {
                    append(it.key, it.value)
                }
            }
            url {
                parameters.append(
                    "name",
                    this@init.domain.name,
                )
                this@init.type.name.let {
                    parameters.append(
                        "type", it
                    )
                }
            }
        }

    val cloudflareBody = dnsRecords.body<CloudflareBody<List<DnsRecord>>>()
    return if (!cloudflareBody.success) {
        error { cloudflareBody.errors }
        warn { "init information error. try after ${this.domain.properties!!.ttl}" }
        false
    } else if (cloudflareBody.result!!.isNotEmpty()) {
        init(cloudflareBody.result!!.first())
        true
    } else {
        info { "no ${this@init.type.name} exists record found for ${this@init.domain.name}" }
        this.exists = false
        true
    }

}

private fun DdnsItem.init(dnsRecord: DnsRecord) {
    debug { "exists dnsRecord: $dnsRecord" }
    this.ttl = dnsRecord.ttl
    this.proxied = dnsRecord.proxied
    this.content = dnsRecord.content
    this.id = dnsRecord.id
    this.inited = true
}

private fun DdnsItem.authHeader(): Map<String, String> {
    return mapOf(
        Pair("Authorization", "Bearer ${this.domain.properties!!.authKey!!}")
    )
}


data class DdnsItem(
    val domain: Domain,
    val type: TYPE,
    val value: String = "",
) {
    var id: String? = null
    var ttl: Int? = null
    var proxied: Boolean? = null
    var inited: Boolean = false
    var exists: Boolean = true
    var content: String = ""
}

enum class TYPE { A, AAAA }

