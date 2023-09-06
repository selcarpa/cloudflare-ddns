import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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


private val json = Json {
    ignoreUnknownKeys = true
}
private val client = HttpClient {
    install(ContentNegotiation) {
        json(json)
    }
    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.BODY
        sanitizeHeader { header -> header == HttpHeaders.Authorization }
    }
}


fun main(args: Array<String>) = runBlocking {
    args.forEach {
        if (it.startsWith("-c=")) {
            ConfigurationUrl = it.replace("-c=", "")
        }
    }

    logger.info { "やらなくて後悔するよりも、やって後悔したほうがいいっていうよね？" }

    val ddnsItems = Configuration.domains.map {
        it.toDDnsItems()
    }.flatten().filterNotNull().toList()

    ddnsItems.groupBy {
        it.domain.properties!!.ttl
    }.forEach { (ttl, d) ->
        d.filter { it.domain.properties?.v4 ?: false }
            .groupBy {
                it.domain.properties!!.checkUrlv4
            }.forEach {
                delayCall(ttl!!.seconds) {
                    ddns(it.value) {
                        getIp(it.key!!)
                    }
                }
            }
        d.filter { it.domain.properties?.v6 ?: false }
            .groupBy {
                it.domain.properties!!.checkUrlv6
            }.forEach {
                delayCall(ttl!!.seconds) {
                    ddns(it.value) {
                        getIp(it.key!!)
                    }
                }
            }
    }

}

fun delayCall(duration: Duration, exec: () -> Unit) = runBlocking {
    while (true) {
        exec()
        delay(duration)
    }
}

private fun ddns(ddnsItems: List<DdnsItem>, ipSupplier: suspend () -> String) = runBlocking {

    val ip = ipSupplier()

    ddnsItems.forEach {
        it.run(ip)
    }
}

suspend fun getIp(checkApi: String): String {
    val response = client.get(checkApi)
    logger.debug { response }
    return response.bodyAsText()
}


private fun Domain.toDDnsItems(): List<DdnsItem?> {
    return listOf(
        if (this.properties!!.v4!!) {
            DdnsItem(
                domain = this, type = TYPE.A
            )
        } else {
            null
        },
        if (this.properties!!.v6!!) {
            DdnsItem(
                domain = this, type = TYPE.AAAA
            )
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
        if (ip == this@run.content) {
            logger.debug { "${this@run.domain} already been resolve to $ip" }
            return@runBlocking
        }

        logger.info { "update ${this@run.domain.name} to ${this@run.type.name} $ip" }
        updateDns(ip, this@run, true)
        return@runBlocking
    }

    logger.info { "create ${this@run.domain.name} with ${this@run.type.name} $ip" }
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
                        type = ddnsItem.type.name!!,
                        name = ddnsItem.domain.name,
                        content = ip,
                        ttl = ddnsItem.ttl!!,
                        proxied = ddnsItem.proxied!!,
                        tags = emptyList()
                    )
                } else {
                    UpdateDnsRecordRequest(
                        type = ddnsItem.type.name!!,
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
                this@init.type.name?.let {
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
        this.exists = false
        true
    }

}

private fun DdnsItem.init(dnsRecord: DnsRecord) {
    logger.debug { dnsRecord }
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

class TYPE(val value: Int, val name: String?) {

    companion object {
        val A = TYPE(1, "A")
        val AAAA = TYPE(28, "AAAA")

    }

    override fun toString(): String {
        return "TYPE(value=$value, name='${name ?: "UNKNOWN"}}')"
    }
}
