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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import model.config.Config.Configuration
import model.config.Config.genConfiguration
import model.config.Config.initConfiguration
import model.config.Domain
import model.request.CloudflareBody
import model.request.DeleteDns
import model.request.DnsRecord
import model.request.DnsRecordRequest
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


private val logger = KotlinLogging.logger {}


@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private var debug = false
private var once = false
private var gen = false
private var help = false
private var purge = false
var genFile = false
lateinit var filePath: String

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

/**
 * main function
 */
fun main(args: Array<String>) = runBlocking {
    try {
        args.forEach {
            println(it)
            argCommandExec(it)
        }

        logAppenderSet()

        logger.debug { "debug-mode online" }
        logger.info { "やらなくて後悔するよりも、やって後悔したほうがいいっていうよね？" }

        if (gen) {
            gen(args)
        }
        if (purge) {//if args contains purge, it will purge all dns record and exit, this task run after configuration loaded
            purge()
            exitGracefully()
        }

        if (help) {
            argCommands.forEach {
                println("${it.name}\t\t${it.description}")
            }
            exitGracefully()
        }
        mainTask()
    } catch (e: Exception) {
        exceptionCatch(e)
    }
}

/**
 * just purge all dns record and exit
 */
private fun purge() {
    Configuration.domains.forEach { domain ->
        domain.toDDnsItems(true).forEach { ddnsItem ->
            ddnsItem.purge(true, "purge task")
        }
    }
    exitGracefully()
}

/**
 * exception catch
 */
private fun exceptionCatch(e: Exception) {
    logger.error(e) {
        e.message
    }
    exitGracefully()
}

/**
 * generate configuration
 */
private fun gen(args: Array<String>) {
    var zoneId: String? = null
    var authKey: String? = null
    var domain: String? = null
    var v4: Boolean? = null
    var v6: Boolean? = null
    args.forEach {
        if (it.startsWith("-zoneId")) {
            zoneId = it.replace("-zoneId=", "")
        }
        if (it.startsWith("-authKey")) {
            authKey = it.replace("-authKey=", "")
        }
        if (it.startsWith("-domain")) {
            domain = it.replace("-domain=", "")
        }
        if (it.startsWith("-v4")) {
            v4 = it.replace("-v4=", "").toBoolean()
        }
        if (it.startsWith("-v6")) {
            v6 = it.replace("-v6=", "").toBoolean()
        }
    }
    if (zoneId == null || authKey == null || domain == null) {
        logger.error { "zoneId, authKey, domain must be specified" }
        exitGracefully()
    }
    genConfiguration(domain, zoneId, authKey, v4, v6)
}

/**
 * to launch ddns task and purge task
 */
private fun CoroutineScope.mainTask() {
    val ddnsItems = Configuration.domains.map { it.toDDnsItems() }.flatten().toList()

    ddnsItems.groupBy {
        it.domain.properties!!.ttl
    }.forEach { (ttl, d) ->
        d.filter { it.type == TYPE.A }.groupBy {
            it.domain.properties!!.checkUrlV4!!
        }.forEach {
            launchMainTask(ttl, it)
        }
        d.filter { it.type == TYPE.AAAA }.groupBy {
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
        if (once) {
            ddns(it.value) {
                getIp(it.key)
            }
        } else {
            delayCall(ttl!!.seconds) {
                ddns(it.value) {
                    getIp(it.key)
                }
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
            logger.error { "purge [${this@purge.domain.name} ${this@purge.type}] failed, try after ${this@purge.domain.properties!!.ttl}" }
        }

        if (!this@purge.exists) {
            logger.info { "no exists record for [${this@purge.domain.name} ${this@purge.type}], purge task stopped" }
            break
        }
        logger.info { "start purge [${this@purge.domain.name} ${this@purge.type}] cause: $reason" }
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
    logger.info { "purge [${this.domain.name} ${this.type}] successful" }
    this.inited = false
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
            logger.warn { "ddns task for [${it.domain.name} ${it.type}] failed, try after ${it.domain.properties!!.ttl}" }
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
 * @param ignoreDisable if true, it will ignore disable flag in config file
 */
private fun Domain.toDDnsItems(ignoreDisable: Boolean = false): List<DdnsItem> {
    return listOfNotNull(
        if (this.properties!!.v4!! || ignoreDisable) {
            this.toV4DdnsItems()
        } else {
            null
        },
        if (this.properties!!.v6!! || ignoreDisable) {
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
                "old record: [${this@run.domain.name} $ip ${proxiedString(this@run.proxied!!)}] (ttl: ${this@run.ttl}), target: [${this@run.content} ${this@run.domain.properties!!.proxied}] (ttl: ${this@run.domain.properties!!.ttl})"
            }
            logger.info {
                "update [${this@run.domain.name} ${this@run.type}] to [$ip ${proxiedString(this@run.domain.properties!!.proxied!!)}] (ttl: ${this@run.domain.properties!!.ttl})"
            }
            updateDns(ip, this@run, true)
            return@runBlocking
        }

        logger.info {
            "checked: [${this@run.domain.name} ${this@run.type}] already been resolve to [$ip ${proxiedString(this@run.domain.properties!!.proxied!!)}]"
        }
        detectReInit()
        return@runBlocking
    } else {
        logger.info {
            "create [${this@run.domain.name} ${this@run.type}] with [$ip ${proxiedString(this@run.domain.properties!!.proxied!!)}]"
        }
        updateDns(ip, this@run)
    }
}

/**
 * detect if task need to reinit
 */
private fun DdnsItem.detectReInit() {
    this.reInitCount += 1
    if (this.domain.properties!!.reInit != 0 && this.domain.properties!!.reInit!! <= this.reInitCount) {
        this.destroy()
    }
}

/**
 * get proxied string
 */
private fun proxiedString(proxied: Boolean) = if (proxied) "proxied" else {
    "not proxied"
}

/**
 * invoke cloudflare api to create or update dns record
 */
@OptIn(ExperimentalTime::class)
suspend fun updateDns(ip: String, ddnsItem: DdnsItem, update: Boolean = false) {
    val authHeader = ddnsItem.authHeader()
    val httpResponse = client.request(
        if (update) {
            "https://api.cloudflare.com/client/v4/zones/${ddnsItem.domain.properties!!.zoneId}/dns_records/${ddnsItem.id}/"
        } else {
            "https://api.cloudflare.com/client/v4/zones/${ddnsItem.domain.properties!!.zoneId}/dns_records/"
        }
    ) {
        setBody(json.encodeToString(DnsRecordRequest(type = ddnsItem.type.name,
            name = ddnsItem.domain.name,
            content = ip,
            ttl = ddnsItem.domain.properties!!.ttl!!,
            proxied = ddnsItem.domain.properties!!.proxied!!,
            tags = emptyList(),
            comment = ddnsItem.domain.properties?.comment ?: "cf-ddns auto update at ${
                run {
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    return@run "${now.year.toString().padStart(4, '0')}-${
                        now.month.number.toString().padStart(2, '0')
                    }-${now.day.toString().padStart(2, '0')} ${
                        now.hour.toString().padStart(2, '0')
                    }:${now.minute.toString().padStart(2, '0')}:${
                        now.second.toString().padStart(2, '0')
                    },${
                        (now.nanosecond / 1000000).toString().padStart(3, '0')
                    } ${TimeZone.currentSystemDefault()}"
                }
            }")))
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
    if (cloudflareBody.success) {
        logger.info {
            "${
                if (update) {
                    "updated"

                } else {
                    "created"
                }
            } [${ddnsItem.domain.name} ${ddnsItem.type}] successful."
        }
        if (cloudflareBody.result!!.name != ddnsItem.domain.name) {
            logger.warn { "But not as expected, expected domain name: ${ddnsItem.domain.name}, actual domain name: ${cloudflareBody.result!!.name}, please check your configuration file. The possible error is that the zone ID is incorrect" }
        }
        ddnsItem.init(cloudflareBody.result!!)
    } else {
        logger.error { "cloudflare api invoke error, The next task will be reinitialized" }
        logger.error { cloudflareBody.errors }
        ddnsItem.inited = false
    }
}

/**
 * invoke cloudflare api to get current dns record
 */
private suspend fun DdnsItem.init(): Boolean {
    logger.info { "init [${this.domain.name} ${this.type}] information from cloudflare" }
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
        logger.warn { "init information error. try after ${this.domain.properties!!.ttl}" }
        logger.error { cloudflareBody.errors }
        false
    } else if (cloudflareBody.result!!.isNotEmpty()) {
        if (cloudflareBody.result!!.size != 1) {
            logger.warn { "more than one record exists for [${this@init.domain.name}], The first record is currently used" }
        }
        init(cloudflareBody.result!!.first())
        true
    } else {
        logger.info { "no exists [${this@init.domain.name} ${this@init.type}] record found" }
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
 * destroy DdnsItem information for reinit
 */
private fun DdnsItem.destroy() {
    this.ttl = null
    this.proxied = null
    this.content = ""
    this.id = null
    this.inited = false
    this.exists = false
    this.reInitCount = 0
}

/**
 * get auth header
 */
private fun DdnsItem.authHeader(): Map<String, String> {
    return mapOf(
        Pair("Authorization", "Bearer ${this.domain.properties!!.authKey!!}")
    )
}

/**
 * ddns task information
 */
data class DdnsItem(
    val domain: Domain, val type: TYPE
) {
    var id: String? = null
    var ttl: Int? = null
    var proxied: Boolean? = null
    var inited: Boolean = false
    var exists: Boolean = false
    var content: String = ""
    var reInitCount = 0
}

enum class TYPE { A, AAAA }

val argCommands: List<ArgCommand> = listOf(
    // configuration load
    ArgCommand("-c", { it.startsWith("-c=") }, { initConfiguration(it.replace("-c=", "")) }, description = """
        Set configuration file path, support json/toml/yaml file
    """.trimIndent()
    ), ArgCommand("-debug", { it.startsWith("-debug") }, {
        debug = true
        debugLogSet()
    }, description = """
        Set debug mode, it will print more log information
        """.trimIndent()
    ), ArgCommand("-once", { it == "-once" }, {
        once = true
    }, description = """
        Run ddns task only once, it can be used with timers such as cron
    """.trimIndent()
    ), ArgCommand("-gen", { it == "-gen" }, {
        gen = true
    }, description = """
        Generate configuration, ignore configuration file path, need to specify zoneId, authKey, domain, v4, v6 in command line, for example: -gen -zoneId=xxx -authKey=xxx -domain=xxx -v4=true -v6=false
    """.trimIndent()
    ), ArgCommand("-file", { it.startsWith("-file") }, {
        genFile = true
        filePath = it.replace("-file=", "")
    }, description = """
        Generate configuration file, need to be use with "-gen", for example: -file=<path to config file>, next time you can use "-c=<path to config file>" to specify this configuration file 
    """.trimIndent()
    ), ArgCommand("-purge", { it == "-purge" }, {
        purge = true
    }, description = """
        Purge all dns record, this is useful when you want to clean up all dns record
    """.trimIndent()
    ), ArgCommand("-help", { it == "-help" }, {
        help = true
    }, description = """
        Print help information
    """.trimIndent()
    )
)

data class ArgCommand(
    val name: String,
    val check: (String) -> Boolean,
    val exec: ((String) -> Unit)? = null,
    val coroutineExec: (suspend (String) -> Unit)? = null,
    val description: String
)

fun argCommandExec(arg: String) = runBlocking {
    argCommands.forEach {
        if (it.check(arg)) {
            it.exec?.invoke(arg)
            launch {
                it.coroutineExec?.invoke(arg)
            }
        }
    }
}
