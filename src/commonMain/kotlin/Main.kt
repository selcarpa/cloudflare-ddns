import exception.CFDdnsException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
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


private val logger = KotlinLogging.logger {}


@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private var debug = false


private val client by lazy {
    HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        if (debug) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.BODY
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }
        }
    }
}

expect fun debugLogSet()

expect fun exitGracefully()


fun main(args: Array<String>) = runBlocking {
    try {
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

        logger.debug { "debug-mode online" }
        logger.info { "やらなくて後悔するよりも、やって後悔したほうがいいっていうよね？" }

        mainTask()
    } catch (e: Exception) {
        if (e is CFDdnsException) {
            exitGracefully()
        } else {
            logger.error(e) {
                e.message
            }
            exitGracefully()
        }
    }
}

/**
 * to launch ddns task and purge task
 */
private fun CoroutineScope.mainTask() {
    val ddnsItems = Configuration.domains.map { it.toDDnsItems() }.flatten().toList()

    ddnsItems.groupBy {
        it.domain.properties!!.ttl
    }.forEach { (ttl, d) ->
        d.filter { it.domain.properties?.v4 ?: false }.groupBy {
            it.domain.properties!!.checkUrlV4
        }.forEach {
            launchTask(ttl, it)
        }
        d.filter { it.domain.properties?.v6 ?: false }.groupBy {
            it.domain.properties!!.checkUrlV6
        }.forEach {
            launchTask(ttl, it)
        }
    }

    //if autoPurge is on, it will purge redundant dns record.
    //for example, if there is a domain in config file, ipv4 is on, ipv6 is off, autoPurge is on. then it will purge ipv6 record.
    Configuration.domains.filter { it.properties?.autoPurge ?: false }
        .filter { it.properties!!.v4!! || it.properties!!.v6!! }.forEach {
            launch(Dispatchers.Default) {
                if (it.properties?.v4 != true) {
                    launch(Dispatchers.Default) {
                        it.toV4DdnsItems().purge()
                    }
                } else if (it.properties?.v6 != true) {
                    launch(Dispatchers.Default) {
                        it.toV6DdnsItems().purge()
                    }
                }
            }
        }
}

fun DdnsItem.purge() = runBlocking {
    while (true) {
        if (!this@purge.inited && !this@purge.init()) {
            logger.error { "purge ${this@purge.domain.name} ${this@purge.type.name} failed, try after ${this@purge.domain.properties!!.ttl}" }
        }

        if (!this@purge.exists) {
            logger.info { "no exists record for ${this@purge.domain.name} ${this@purge.type.name}, purge task stopped" }
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
        "https://api.cloudflare.com/client/v4/zones/${ddnsItem.domain.properties!!.zoneId}/dns_records/${ddnsItem.id}/"
    ) {
        headers {
            authHeader.forEach {
                append(it.key, it.value)
            }
        }

    }
    val cloudflareBody = httpResponse.body<CloudflareBody<Any>>()
    if (!cloudflareBody.success) {
        logger.error { cloudflareBody.errors }
        return false
    }
    logger.info { "purge ${ddnsItem.domain.name} ${ddnsItem.type.name} successful" }
    return true
}

private fun CoroutineScope.launchTask(
    ttl: Int?, it: Map.Entry<String?, List<DdnsItem>>
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
                logger.error(e) {
                    e.message
                }
            }
        }
        delay(duration)
    }
}

private fun ddns(ddnsItems: List<DdnsItem>, ipSupplier: suspend () -> String) = runBlocking {
    logger.debug { "start ${ddnsItems[0].type} ddns task for ${(ddnsItems.joinToString(",") { it.domain.name })}" }
    val ip = ipSupplier()

    ddnsItems.forEach {
        it.run(ip)
    }
}

suspend fun getIp(checkApi: String): String {
    val response = client.get(checkApi)
    logger.debug { response }
    val ip = response.bodyAsText()
    logger.debug { "get current ip: $ip from $checkApi" }
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
    return listOfNotNull(
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
    )
}

private fun DdnsItem.run(ip: String) = runBlocking {
    if (!this@run.inited && !this@run.init()) {
        return@runBlocking
    }

    if (this@run.exists) {
        if (ip != this@run.content // if ip on cloudflare not equals to current ip
            || this@run.proxied != this@run.domain.properties!!.proxied  // if proxied on cloudflare not equals to config
//            || this@run.ttl != this@run.domain.properties!!.ttl // if ttl on cloudflare not equals to config
        ) {
            logger.info {
                "update ${this@run.domain.name} to ${this@run.type.name} $ip ${proxiedString()}"
            }
            updateDns(ip, this@run, true)
            return@runBlocking
        }

        logger.info {
            "checked: ${this@run.type} ${this@run.domain.name} already been resolve to $ip ${proxiedString()}"
        }
        return@runBlocking
    } else {
        logger.info {
            "create ${this@run.domain.name} with ${this@run.type.name} $ip ${proxiedString()}"
        }
        updateDns(ip, this@run)
    }
}

private fun DdnsItem.proxiedString() = if (this.domain.properties!!.proxied == false) "not proxied" else {
    "proxied"
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
        logger.error { cloudflareBody.errors }
    }
    if (update) {
        logger.info { "updated ${ddnsItem.domain.name} to $ip successful" }
    } else {
        logger.info { "created ${ddnsItem.domain.name} with $ip successful" }
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
        logger.error { cloudflareBody.errors }
        logger.warn { "init information error. try after ${this.domain.properties!!.ttl}" }
        false
    } else if (cloudflareBody.result!!.isNotEmpty()) {
        init(cloudflareBody.result!!.first())
        true
    } else {
        logger.info { "no ${this@init.type.name} exists record found for ${this@init.domain.name}" }
        true
    }

}

private fun DdnsItem.init(dnsRecord: DnsRecord) {
    logger.debug { "exists dnsRecord: $dnsRecord" }
    this.ttl = dnsRecord.ttl
    this.proxied = dnsRecord.proxied
    this.content = dnsRecord.content
    this.id = dnsRecord.id
    this.inited = true
    this.exists = true
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
    var exists: Boolean = false
    var content: String = ""
}

enum class TYPE { A, AAAA }

