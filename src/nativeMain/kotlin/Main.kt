import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
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
private val logger = KotlinLogging.logger {}
private val client = HttpClient {
    install(ContentNegotiation) {
        json(json)
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
        d.groupBy {
            it.domain.properties!!.checkUrlv4
        }.forEach {
            delayCall(ttl!!.seconds) {
                ddns(it.value) {
                    getIp(it.key!!)
                }
            }
        }
        d.groupBy {
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
    exec()
    delay(duration)
}

private fun ddns(ddnsItems: List<DdnsItem>, ipSupplier: suspend () -> String) = runBlocking {

    val ipv4 = ipSupplier()

    ddnsItems.forEach {
        it.run(ipv4)
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

        updateDns(ip, this@run, true)
        return@runBlocking
    }

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
                UpdateDnsRecordRequest(
                    type = ddnsItem.type.name!!,
                    name = ddnsItem.domain.name,
                    content = ip,
                    ttl = ddnsItem.ttl!!,
                    proxied = ddnsItem.proxied!!,
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
            parameters {
                append(
                    "name",
                    this@init.domain.name,
                )
                this@init.type.name?.let {
                    append(
                        "type", it
                    )
                }
            }


        }

    val cloudflareBody = dnsRecords.body<CloudflareBody<DnsRecord>>()
    return if (!cloudflareBody.success) {
        logger.error { cloudflareBody.errors }
        logger.error { "init information error. try after ${this.domain.properties!!.ttl}" }
        false
    } else if (cloudflareBody.result.isNotEmpty()) {
        logger.debug { cloudflareBody.result }
        this.ttl = cloudflareBody.result.first().ttl
        this.proxied = cloudflareBody.result.first().proxied
        this.content = cloudflareBody.result.first().content
        this.id = cloudflareBody.result.first().id
        this.inited = true
        true
    } else {
        logger.info { "no exists record found" }
        this.exists = false
        true
    }

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
