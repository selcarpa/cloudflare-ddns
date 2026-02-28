import cli.CliParser
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
import model.config.Config
import model.config.Config.Configuration
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

/**
 * JSON configuration for serialization
 */
@OptIn(ExperimentalSerializationApi::class)
private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * HTTP client with lazy initialization
 */
private val client by lazy {
    HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        if (AppContext.isDebugEnabled) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.BODY
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }
        }
    }
}

/**
 * Application context for managing global state
 */
object AppContext {
    var isDebugEnabled: Boolean = false
    var isOnceMode: Boolean = false
    var shouldGenerateConfig: Boolean = false
    var shouldPurge: Boolean = false
    var shouldShowHelp: Boolean = false
    var outputPath: String? = null
}

/**
 * Application entry point
 */
fun main(args: Array<String>) = runBlocking {
    try {
        // Parse command-line arguments
        val cliArgs = CliParser.parse(args)
        initializeApplicationContext(cliArgs)
        
        // Setup logging
        setupLogging()
        
        // Log startup message
        logger.info { Constants.Logging.STARTUP_MESSAGE }
        
        // Handle commands
        when {
            cliArgs.shouldShowHelp -> handleHelp()
            cliArgs.shouldGenerateConfig -> handleGenerateConfig(cliArgs)
            cliArgs.shouldPurge -> handlePurge()
            else -> runMainTask()
        }
    } catch (e: Exception) {
        handleException(e)
    }
}

/**
 * Initialize application context from CLI arguments
 */
private fun initializeApplicationContext(cliArgs: cli.CliArgs) {
    AppContext.isDebugEnabled = cliArgs.isDebugEnabled
    AppContext.isOnceMode = cliArgs.isOnceMode
    AppContext.shouldGenerateConfig = cliArgs.shouldGenerateConfig
    AppContext.shouldPurge = cliArgs.shouldPurge
    AppContext.shouldShowHelp = cliArgs.shouldShowHelp
    AppContext.outputPath = cliArgs.outputPath
}

/**
 * Setup logging based on debug flag
 */
private fun setupLogging() {
    if (AppContext.isDebugEnabled) {
        logger.info { Constants.Logging.DEBUG_MODE_MESSAGE }
    }
}

/**
 * Handle help command
 */
private fun handleHelp() {
    CliParser.printHelp()
    exitGracefully()
}

/**
 * Handle configuration generation
 */
private suspend fun handleGenerateConfig(cliArgs: cli.CliArgs) {
    if (!CliParser.validateGenArgs(cliArgs)) {
        logger.error { Constants.ErrorMessages.GEN_MISSING_PARAMS }
        exitGracefully()
    }
    
    Config.genConfiguration(
        domain = cliArgs.domain!!,
        zoneId = cliArgs.zoneId!!,
        authKey = cliArgs.authKey!!,
        v4 = cliArgs.v4,
        v6 = cliArgs.v6
    )
}

/**
 * Handle purge command
 */
private fun handlePurge() {
    Configuration.domains.forEach { domain ->
        domain.toDDnsItems(ignoreDisable = true).forEach { ddnsItem ->
            ddnsItem.purge(loopExec = true, reason = "purge task")
        }
    }
    exitGracefully()
}

/**
 * Run main DDNS task
 */
private fun runMainTask() {
    mainTask()
}

/**
 * Handle uncaught exceptions
 */
private fun handleException(e: Exception) {
    logger.error(e) { e.message ?: "Unknown error occurred" }
    exitGracefully()
}

/**
 * Launch DDNS and auto-purge tasks
 */
private fun CoroutineScope.mainTask() {
    val ddnsItems = Configuration.domains.map { it.toDDnsItems() }.flatten()

    // Group DDNS tasks by TTL and IP check URL for efficient scheduling
    ddnsItems.groupBy { it.domain.properties!!.ttl }
        .forEach { (ttl, domains) ->
            // Handle IPv4 tasks
            domains.filter { it.type == TYPE.A }
                .groupBy { it.domain.properties!!.checkUrlV4!! }
                .forEach { launchMainTask(ttl, it) }
            
            // Handle IPv6 tasks
            domains.filter { it.type == TYPE.AAAA }
                .groupBy { it.domain.properties!!.checkUrlV6!! }
                .forEach { launchMainTask(ttl, it) }
        }

    // Launch auto-purge tasks for domains with autoPurge enabled
    Configuration.domains
        .filter { it.properties?.autoPurge == true }
        .filter { it.properties!!.v4 == true || it.properties!!.v6 == true }
        .forEach { domain ->
            launch(Dispatchers.Default) {
                when {
                    domain.properties?.v4 != true -> {
                        domain.toV4DdnsItems().purge(loopExec = true, reason = "IPv4 disabled in config")
                    }
                    domain.properties?.v6 != true -> {
                        domain.toV6DdnsItems().purge(loopExec = true, reason = "IPv6 disabled in config")
                    }
                }
            }
        }
}

/**
 * Launch a single DDNS task with scheduling
 */
private fun CoroutineScope.launchMainTask(
    ttl: Int?, 
    ipCheckGroup: Map.Entry<String, List<DdnsItem>>
) {
    launch(Dispatchers.Default) {
        if (AppContext.isOnceMode) {
            executeDdnsTask(ipCheckGroup.value) {
                getIp(ipCheckGroup.key)
            }
        } else {
            delayAndRepeat(ttl!!.seconds) {
                executeDdnsTask(ipCheckGroup.value) {
                    getIp(ipCheckGroup.key)
                }
            }
        }
    }
}

/**
 * Execute DDNS task for a group of domains
 */
private fun executeDdnsTask(ddnsItems: List<DdnsItem>, ipSupplier: suspend () -> String) = runBlocking {
    logger.debug { 
        "Starting ${ddnsItems[0].type} DDNS task for: ${ddnsItems.joinToString(", ") { it.domain.name }}" 
    }
    
    runCatching {
        ipSupplier()
    }.onSuccess { currentIp ->
        ddnsItems.forEach { it.updateWithCurrentIp(currentIp) }
    }.onFailure { e ->
        val errorMessage = if (AppContext.isDebugEnabled) {
            e.message
        } else {
            e.message ?: "Unknown error"
        }
        logger.error { errorMessage }
        
        ddnsItems.forEach {
            logger.warn { 
                "DDNS task failed for [${it.domain.name} ${it.type}], retry in ${it.domain.properties!!.ttl}s" 
            }
        }
        
        // Auto-purge if enabled and IP detection failed
        ddnsItems.filter { it.domain.properties?.autoPurge == true }
            .forEach { it.purge(reason = "IP detection failed") }
    }
}

/**
 * Graceful exit
 */
fun exitGracefully() {
    // Cleanup resources if needed
    client.close()
    throw ExitException()
}

/**
 * Exception for graceful exit
 */
class ExitException : Exception()

// ============================================================================
// DDNS Item Management
// ============================================================================

/**
 * DDNS task data class
 */
data class DdnsItem(
    val domain: Domain, 
    val type: TYPE
) {
    var id: String? = null
    var ttl: Int? = null
    var proxied: Boolean? = null
    var inited: Boolean = false
    var exists: Boolean = false
    var content: String = ""
    var reInitCount: Int = 0
    
    /**
     * Update DNS record with current IP
     */
    fun updateWithCurrentIp(ip: String) = runBlocking {
        if (!inited && !init()) {
            return@runBlocking
        }

        if (exists) {
            if (needsUpdate(ip)) {
                logger.debug { 
                    "Old record: [${domain.name} $ip ${proxiedString(proxied!!)}] (TTL: $ttl), " +
                    "Target: [${domain.properties!!.proxied}] (TTL: ${domain.properties!!.ttl})"
                }
                logger.info { 
                    "Updating [${domain.name} ${type}] to [$ip ${proxiedString(domain.properties!!.proxied!!)}] " +
                    "(TTL: ${domain.properties!!.ttl})"
                }
                updateDns(ip, update = true)
                return@runBlocking
            }

            logger.info { 
                "Checked: [${domain.name} ${type}] already resolves to [$ip ${proxiedString(domain.properties!!.proxied!!)}]" 
            }
            checkReInit()
            return@runBlocking
        } else {
            logger.info { 
                "Creating [${domain.name} ${type}] with [$ip ${proxiedString(domain.properties!!.proxied!!)}]" 
            }
            updateDns(ip, update = false)
        }
    }
    
    /**
     * Check if DNS record needs update
     */
    private fun needsUpdate(currentIp: String): Boolean {
        return currentIp != content ||
                proxied != domain.properties!!.proxied ||
                (domain.properties!!.ttlCheck == true && ttl != domain.properties!!.ttl)
    }
    
    /**
     * Check if re-initialization is needed
     */
    private fun checkReInit() {
        reInitCount++
        val reInitInterval = domain.properties!!.reInit ?: 0
        if (reInitInterval != 0 && reInitInterval <= reInitCount) {
            destroy()
        }
    }
    
    /**
     * Destroy DDNS item state for re-initialization
     */
    private fun destroy() {
        ttl = null
        proxied = null
        content = ""
        id = null
        inited = false
        exists = false
        reInitCount = 0
    }
    
    /**
     * Get authorization header for Cloudflare API
     */
    fun authHeader(): Map<String, String> {
        return mapOf("Authorization" to "Bearer ${domain.properties!!.authKey!!}")
    }
}

/**
 * DNS record type enum
 */
enum class TYPE { A, AAAA }

/**
 * Convert domain to IPv4 DDNS item
 */
private fun Domain.toV4DdnsItems(): DdnsItem {
    return DdnsItem(domain = this, type = TYPE.A)
}

/**
 * Convert domain to IPv6 DDNS item
 */
private fun Domain.toV6DdnsItems(): DdnsItem {
    return DdnsItem(domain = this, type = TYPE.AAAA)
}

/**
 * Convert domain to list of DDNS items
 */
private fun Domain.toDDnsItems(ignoreDisable: Boolean = false): List<DdnsItem> {
    return listOfNotNull(
        if (properties!!.v4 == true || ignoreDisable) toV4DdnsItems() else null,
        if (properties!!.v6 == true || ignoreDisable) toV6DdnsItems() else null
    )
}

// ============================================================================
// Purge Operations
// ============================================================================

/**
 * Purge DNS record task
 * @param loopExec if true, retry until successful
 * @param reason reason for purge
 */
fun DdnsItem.purge(loopExec: Boolean = false, reason: String) = runBlocking {
    do {
        if (!inited && !init()) {
            logger.error { 
                "Purge failed for [${domain.name} ${type}], retry in ${domain.properties!!.ttl}s" 
            }
        }

        if (!exists) {
            logger.info { "No existing record for [${domain.name} ${type}], purge stopped" }
            break
        }
        
        logger.info { "Purging [${domain.name} ${type}] - Reason: $reason" }
        
        if (doPurge()) {
            break
        }
        
        delay(domain.properties!!.ttl!!.seconds)
    } while (loopExec)
}

/**
 * Invoke Cloudflare API to delete DNS record
 */
suspend fun DdnsItem.doPurge(): Boolean {
    val authHeader = authHeader()
    val httpResponse = client.delete(
        "${Constants.CLOUDFLARE_API_BASE}/zones/${domain.properties!!.zoneId}/dns_records/${id}/"
    ) {
        headers {
            authHeader.forEach { append(it.key, it.value) }
        }
    }
    
    val cloudflareBody = httpResponse.body<CloudflareBody<DeleteDns>>()
    if (!cloudflareBody.success) {
        logger.error { cloudflareBody.errors }
        return false
    }
    
    logger.info { "Purged [${domain.name} ${type}] successfully" }
    inited = false
    return true
}

// ============================================================================
// DNS Record Update Operations
// ============================================================================

/**
 * Create or update DNS record via Cloudflare API
 */
@OptIn(ExperimentalTime::class)
suspend fun DdnsItem.updateDns(ip: String, update: Boolean = false) {
    val authHeader = authHeader()
    val endpoint = if (update) {
        "${Constants.CLOUDFLARE_API_BASE}/zones/${domain.properties!!.zoneId}/dns_records/${id}/"
    } else {
        "${Constants.CLOUDFLARE_API_BASE}/zones/${domain.properties!!.zoneId}/dns_records/"
    }
    
    val comment = domain.properties?.comment ?: buildDefaultComment()
    
    val httpResponse = client.request(endpoint) {
        setBody(json.encodeToString(
            DnsRecordRequest(
                type = type.name,
                name = domain.name,
                content = ip,
                ttl = domain.properties!!.ttl!!,
                proxied = domain.properties!!.proxied!!,
                tags = emptyList(),
                comment = comment
            )
        ))
        headers {
            authHeader.forEach { append(it.key, it.value) }
        }
        method = if (update) HttpMethod.Put else HttpMethod.Post
    }
    
    val cloudflareBody = httpResponse.body<CloudflareBody<DnsRecord>>()
    if (cloudflareBody.success) {
        val action = if (update) "Updated" else "Created"
        logger.info { "$action [${domain.name} ${type}] successfully" }
        
        if (cloudflareBody.result!!.name != domain.name) {
            logger.warn { 
                "Unexpected domain name: expected=${domain.name}, actual=${cloudflareBody.result!!.name}. " +
                "Please verify your Zone ID configuration."
            }
        }
        init(cloudflareBody.result!!)
    } else {
        logger.error { "Cloudflare API error - will reinitialize on next attempt" }
        logger.error { cloudflareBody.errors }
        inited = false
    }
}

/**
 * Build default comment with timestamp
 */
@OptIn(ExperimentalTime::class)
private fun buildDefaultComment(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return "cf-ddns auto update at " +
            "${now.year.toString().padStart(4, '0')}-" +
            "${now.month.number.toString().padStart(2, '0')}-" +
            "${now.day.toString().padStart(2, '0')} " +
            "${now.hour.toString().padStart(2, '0')}:" +
            "${now.minute.toString().padStart(2, '0')}:" +
            "${now.second.toString().padStart(2, '0')}," +
            "${(now.nanosecond / 1000000).toString().padStart(3, '0')} " +
            "${TimeZone.currentSystemDefault()}"
}

// ============================================================================
// DNS Record Initialization
// ============================================================================

/**
 * Initialize DDNS item by fetching DNS record from Cloudflare
 */
private suspend fun DdnsItem.init(): Boolean {
    logger.info { "Initializing [${domain.name} ${type}] from Cloudflare" }
    
    val authHeader = authHeader()
    val httpResponse = client.get(
        "${Constants.CLOUDFLARE_API_BASE}/zones/${domain.properties!!.zoneId}/dns_records"
    ) {
        headers {
            authHeader.forEach { append(it.key, it.value) }
        }
        url {
            parameters.append("name", domain.name)
            parameters.append("type", type.name)
        }
    }
    
    val cloudflareBody = httpResponse.body<CloudflareBody<List<DnsRecord>>>()
    
    return when {
        !cloudflareBody.success -> {
            logger.warn { "Failed to initialize, retry in ${domain.properties!!.ttl}s" }
            logger.error { cloudflareBody.errors }
            false
        }
        cloudflareBody.result!!.isNotEmpty() -> {
            if (cloudflareBody.result!!.size > 1) {
                logger.warn { "Multiple records found for [${domain.name}], using first record" }
            }
            init(cloudflareBody.result!!.first())
            true
        }
        else -> {
            logger.info { "No existing [${domain.name} ${type}] record found" }
            true
        }
    }
}

/**
 * Initialize DDNS item from DNS record data
 */
private fun DdnsItem.init(dnsRecord: DnsRecord) {
    logger.debug { "DNS record: $dnsRecord" }
    ttl = dnsRecord.ttl
    proxied = dnsRecord.proxied
    content = dnsRecord.content
    id = dnsRecord.id
    inited = true
    exists = true
}

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Get current public IP from check service
 */
suspend fun getIp(checkUrl: String): String {
    val response = client.get(checkUrl)
    logger.debug { "IP check response: $response" }
    val ip = response.bodyAsText()
    logger.debug { "Current IP: $ip (from: $checkUrl)" }
    return ip
}

/**
 * Convert proxied boolean to human-readable string
 */
private fun proxiedString(proxied: Boolean): String = if (proxied) "proxied" else "not proxied"

/**
 * Delay and repeatedly execute a function
 */
fun delayAndRepeat(duration: Duration, exec: suspend () -> Unit) = runBlocking {
    while (true) {
        launch(Dispatchers.Default) {
            runCatching {
                exec()
            }.onFailure { e ->
                logger.error(e) { e.message }
            }
        }
        delay(duration)
    }
}
