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
import model.request.DeleteDns
import model.request.DnsRecord
import model.request.DnsRecordRequest
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


/**
 * main function
 */
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

        logAppenderSet()

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

expect fun logAppenderSet()

/**
 * to launch ddns task and purge task
 */
private fun CoroutineScope.mainTask() {
    val ddnsItems = Configuration.domains.map { it.toDDnsItems() }.flatten().toList()

    ddnsItems.groupBy {
        it.domain.properties!!.ttl
    }.forEach { (ttl, d) ->
        d.filter { it.domain.properties?.v4 ?: false }.groupBy {
            it.domain.properties!!.checkUrlV4!!
        }.forEach {
            launchMainTask(ttl, it)
        }
        d.filter { it.domain.properties?.v6 ?: false }.groupBy {
            it.domain.properties!!.checkUrlV6!!
        }.forEach {
            launchMainTask(ttl, it)
        }
    }

    //if autoPurge is on, it will purge redundant dns record.
    //for example, if there is a domain in config file, ipv4 is on, ipv6 is off, autoPurge is on. then it will purge ipv6 record.
    Configuration.domains.filter { it.properties?.autoPurge ?: false }
        .filter { it.properties!!.v4!! || it.properties!!.v6!! }.forEach {
            launch(Dispatchers.Default) {
                if (it.properties?.v4 != true) {
                    launch(Dispatchers.Default) {
                        it.toV4DdnsItems().purge(true, "Ipv4 record is disabled in config file")
                    }
                } else if (it.properties?.v6 != true) {
                    launch(Dispatchers.Default) {
                        it.toV6DdnsItems().purge(true, "Ipv6 record is disabled in config file")
                    }
                }
            }
        }
}

/**
 * launch main ddns task
 */
private fun CoroutineScope.launchMainTask(
    ttl: Int?, it: Map.Entry<String, List<DdnsItem>>
) {
    launch(Dispatchers.Default) {
        delayCall(ttl!!.seconds) {
            ddns(it.value) {
                getIp(it.key)
            }
        }
    }
}

/**
 * purge task
 * @param loopExec if true, it will loop to purge until purge successful
 */
fun DdnsItem.purge(loopExec: Boolean = false, reason: String) = runBlocking {
    do {
        if (!this@purge.inited && !this@purge.init()) {
            logger.error { "purge ${this@purge.domain.name} ${this@purge.type} failed, try after ${this@purge.domain.properties!!.ttl}" }
        }

        if (!this@purge.exists) {
            logger.info { "no exists record for ${this@purge.domain.name} ${this@purge.type}, purge task stopped" }
            break
        }
        logger.info { "start purge ${this@purge.domain.name} ${this@purge.type} cause: $reason" }
        if (this@purge.doPurge()) {
            break
        }
        delay(this@purge.domain.properties!!.ttl!!.seconds)
    } while (loopExec)
}

/**
 * invoke cloudflare api to purge dns record
 */
suspend fun DdnsItem.doPurge(): Boolean {
    val authHeader = this.authHeader()
    val httpResponse = client.delete(
        "https://api.cloudflare.com/client/v4/zones/${this.domain.properties!!.zoneId}/dns_records/${this.id}/"
    ) {
        headers {
            authHeader.forEach {
                append(it.key, it.value)
            }
        }

    }
    val cloudflareBody = httpResponse.body<CloudflareBody<DeleteDns>>()
    if (!cloudflareBody.success) {
        logger.error { cloudflareBody.errors }
        return false
    }
    logger.info { "purge ${this.domain.name} ${this.type} successful" }
    return true
}


/**
 * delay and loop to call exec function
 */
fun delayCall(duration: Duration, exec: () -> Unit) = runBlocking {
    while (true) {
        //https://kotlinlang.org/docs/coroutines-and-channels.html#using-the-outer-scope-s-context
        //https://kotlinlang.org/docs/exception-handling.html
        launch(Dispatchers.Default) {
            runCatching {
                exec()
            }.onFailure {
                logger.error(it) {
                    it.message
                }
            }
        }
        delay(duration)
    }
}

/**
 * ddns task
 */
private fun ddns(ddnsItems: List<DdnsItem>, ipSupplier: suspend () -> String) = runBlocking {
    logger.debug { "start ${ddnsItems[0].type} ddns task for ${(ddnsItems.joinToString(",") { it.domain.name })}" }
    runCatching {
        ipSupplier()
    }.onSuccess {
        ddnsItems.forEach { ddnsItem ->
            ddnsItem.run(it)
        }
    }.onFailure { e ->
        if (debug) {
            logger.error(e) { e.message }
        } else {
            logger.error { e.message }
        }
        ddnsItems.forEach {
            logger.warn { "ddns task for ${it.domain.name} ${it.type} failed, try after ${it.domain.properties!!.ttl}" }
        }
        ddnsItems.filter { it.domain.properties?.autoPurge!! }.forEach { ddnsItem ->
            ddnsItem.purge(reason = "get ip failed, so purge it")
        }
    }
}

/**
 * get current ip by checkApi
 * @param checkApi checkApi url
 */
suspend fun getIp(checkApi: String): String {
    val response = client.get(checkApi)
    logger.debug { response }
    val ip = response.bodyAsText()
    logger.debug { "get current ip: $ip from $checkApi" }
    return ip
}

/**
 * convert domain to ddns item
 */
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

/**
 * convert domain to ddns item list
 */
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

/**
 * run ddns task, include init task, detect if need to create or update dns record
 * @param ip current ip
 */
private fun DdnsItem.run(ip: String) = runBlocking {
    if (!this@run.inited && !this@run.init()) {
        return@runBlocking
    }

    if (this@run.exists) {
        if (ip != this@run.content // if ip on cloudflare not equals to current ip
            || this@run.proxied != this@run.domain.properties!!.proxied  // if proxied on cloudflare not equals to config
            || (this@run.domain.properties!!.ttlCheck!! && this@run.ttl != this@run.domain.properties!!.ttl) //check if ttl on cloudflare not equals to config when ttlCheck on
        ) {
            logger.debug {
                "old record for ${this@run.domain.name} is $ip ${proxiedString(this@run.proxied!!)} (${this@run.ttl}), target is ${this@run.content} ${this@run.domain.properties!!.proxied} (${this@run.domain.properties!!.ttl})"
            }
            logger.info {
                "update ${this@run.domain.name} to ${this@run.type} $ip ${proxiedString(this@run.domain.properties!!.proxied!!)} (${this@run.domain.properties!!.ttl})"
            }
            updateDns(ip, this@run, true)
            return@runBlocking
        }

        logger.info {
            "checked: ${this@run.domain.name} ${this@run.type} already been resolve to $ip ${proxiedString(this@run.domain.properties!!.proxied!!)}"
        }
        return@runBlocking
    } else {
        logger.info {
            "create ${this@run.domain.name} ${this@run.type} with $ip ${proxiedString(this@run.domain.properties!!.proxied!!)}"
        }
        updateDns(ip, this@run)
    }
}

/**
 * get proxied string
 */
private fun proxiedString(proxied: Boolean) = if (proxied) "not proxied" else {
    "proxied"
}

/**
 * invoke cloudflare api to create or update dns record
 */
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
                DnsRecordRequest(
                    type = ddnsItem.type.name,
                    name = ddnsItem.domain.name,
                    content = ip,
                    ttl = ddnsItem.domain.properties!!.ttl!!,
                    proxied = ddnsItem.domain.properties!!.proxied!!,
                    tags = emptyList()
                )
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
        logger.info { "updated ${ddnsItem.domain.name} successful" }
    } else {
        logger.info { "created ${ddnsItem.domain.name} successful" }
    }
    ddnsItem.init(cloudflareBody.result!!)
}

/**
 * invoke cloudflare api to get current dns record
 */
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
                this@init.type.let {
                    parameters.append(
                        "type", it.name
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
        logger.info { "no exists ${this@init.domain.name} ${this@init.type} record found" }
        true
    }

}

/**
 * init DdnsItem by cloudflare api result
 */
private fun DdnsItem.init(dnsRecord: DnsRecord) {
    logger.debug { "exists dnsRecord: $dnsRecord" }
    this.ttl = dnsRecord.ttl
    this.proxied = dnsRecord.proxied
    this.content = dnsRecord.content
    this.id = dnsRecord.id
    this.inited = true
    this.exists = true
}

/**
 * get auth header
 */
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

