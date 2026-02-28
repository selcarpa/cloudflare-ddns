package cli

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Command-line argument definitions
 */
enum class ArgType(val name: String, val description: String) {
    CONFIG("-c=<path>", "Specify configuration file path"),
    ONCE("-once", "Run once and exit (for cron)"),
    GEN("-gen", "Generate configuration template"),
    PURGE("-purge", "Remove all DNS records and exit"),
    DEBUG("-debug", "Enable debug logging"),
    HELP("-help", "Show help message"),
    FILE("-file=<path>", "Output path for -gen option"),
    ZONE_ID("-zoneId=<id>", "Cloudflare Zone ID (used with -gen)"),
    AUTH_KEY("-authKey=<key>", "Cloudflare API Token (used with -gen)"),
    DOMAIN("-domain=<name>", "Domain name (used with -gen)"),
    V4("-v4=<bool>", "Enable IPv4 (used with -gen)"),
    V6("-v6=<bool>", "Enable IPv6 (used with -gen)");

    companion object {
        fun fromArg(arg: String): ArgType? {
            return values().find { type ->
                if (arg.contains("=")) {
                    arg.startsWith(type.name.substringBefore("="))
                } else {
                    arg == type.name
                }
            }
        }
    }
}

/**
 * Parsed command-line arguments
 */
data class CliArgs(
    val configPath: String? = null,
    val once: Boolean = false,
    val gen: Boolean = false,
    val purge: Boolean = false,
    val debug: Boolean = false,
    val help: Boolean = false,
    val outputPath: String? = null,
    val zoneId: String? = null,
    val authKey: String? = null,
    val domain: String? = null,
    val v4: Boolean? = null,
    val v6: Boolean? = null
) {
    val shouldGenerateConfig: Boolean get() = gen
    val shouldPurge: Boolean get() = purge
    val shouldShowHelp: Boolean get() = help
    val isOnceMode: Boolean get() = once
    val isDebugEnabled: Boolean get() = debug
}

/**
 * Command-line argument parser
 */
object CliParser {
    
    /**
     * Parse command-line arguments
     */
    fun parse(args: Array<String>): CliArgs {
        var configPath: String? = null
        var once = false
        var gen = false
        var purge = false
        var debug = false
        var help = false
        var outputPath: String? = null
        var zoneId: String? = null
        var authKey: String? = null
        var domain: String? = null
        var v4: Boolean? = null
        var v6: Boolean? = null

        args.forEach { arg ->
            when {
                arg.startsWith("-c=") -> configPath = arg.removePrefix("-c=")
                arg == "-once" -> once = true
                arg == "-gen" -> gen = true
                arg == "-purge" -> purge = true
                arg == "-debug" -> debug = true
                arg == "-help" -> help = true
                arg.startsWith("-file=") -> outputPath = arg.removePrefix("-file=")
                arg.startsWith("-zoneId=") -> zoneId = arg.removePrefix("-zoneId=")
                arg.startsWith("-authKey=") -> authKey = arg.removePrefix("-authKey=")
                arg.startsWith("-domain=") -> domain = arg.removePrefix("-domain=")
                arg.startsWith("-v4=") -> v4 = arg.removePrefix("-v4=").toBooleanStrictOrNull()
                arg.startsWith("-v6=") -> v6 = arg.removePrefix("-v6=").toBooleanStrictOrNull()
                else -> logger.warn { "Unknown argument: $arg" }
            }
        }

        return CliArgs(
            configPath = configPath,
            once = once,
            gen = gen,
            purge = purge,
            debug = debug,
            help = help,
            outputPath = outputPath,
            zoneId = zoneId,
            authKey = authKey,
            domain = domain,
            v4 = v4,
            v6 = v6
        )
    }

    /**
     * Print help message
     */
    fun printHelp() {
        println("Cloudflare-DDNS - Dynamic DNS client for Cloudflare")
        println()
        println("Usage:")
        println("  cf-ddns [options]")
        println()
        println("Options:")
        ArgType.values().forEach { type ->
            println("  ${type.name.padEnd(20)} ${type.description}")
        }
        println()
        println("Examples:")
        println("  cf-ddns -c=config.json5")
        println("  cf-ddns -gen -zoneId=xxx -authKey=xxx -domain=example.com -v4=true -v6=false")
        println("  cf-ddns -once -c=config.json5")
    }

    /**
     * Validate generation arguments
     */
    fun validateGenArgs(args: CliArgs): Boolean {
        if (args.zoneId.isNullOrBlank()) {
            logger.error { "zoneId is required for configuration generation" }
            return false
        }
        if (args.authKey.isNullOrBlank()) {
            logger.error { "authKey is required for configuration generation" }
            return false
        }
        if (args.domain.isNullOrBlank()) {
            logger.error { "domain is required for configuration generation" }
            return false
        }
        return true
    }
}
