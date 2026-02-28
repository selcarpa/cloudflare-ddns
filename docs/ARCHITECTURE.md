# Cloudflare-DDNS Architecture

## Overview

Cloudflare-DDNS is a Kotlin Multiplatform application that provides Dynamic DNS (DDNS) services based on Cloudflare's DNS API. It automatically updates DNS records when the public IP address changes.

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Cloudflare-DDNS Application                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐       │
│  │  Main.kt     │    │  Config      │    │  Cloudflare  │       │
│  │  (Entry)     │───▶│  Parser      │───▶│  API Client  │       │
│  └──────────────┘    └──────────────┘    └──────────────┘       │
│         │                                       │                │
│         ▼                                       ▼                │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐       │
│  │  Scheduler   │    │  IP Detector │    │  DNS Manager │       │
│  │  (TTL-based) │    │  (IPv4/v6)   │    │  (CRUD)      │       │
│  └──────────────┘    └──────────────┘    └──────────────┘       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │   Cloudflare    │
                    │   DNS API       │
                    └─────────────────┘
```

## Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Kotlin Multiplatform | 2.2.0 |
| HTTP Client | Ktor Client | 3.2.2 |
| JSON Serialization | kotlinx-serialization | 1.9.0 |
| Date/Time | kotlinx-datetime | 0.7.1 |
| TOML Parser | tomlkt | - |
| Logging | kotlin-logging | - |
| IO | Okio | - |

## Module Structure

```
src/
├── commonMain/
│   └── kotlin/
│       ├── Main.kt                      # Application entry point
│       ├── Expect.kt                    # Platform-specific declarations
│       ├── model/
│       │   ├── config/
│       │   │   └── ConfigurationSetting.kt  # Configuration models
│       │   └── request/
│       │       └── Cloudflare API request models
│       ├── utils/                       # Utility functions
│       └── exception/                   # Custom exceptions
├── linuxX64Main/                        # Linux x64 specific
├── linuxArm64Main/                      # Linux ARM64 specific
├── mingwX64Main/                        # Windows x64 specific
└── jvmMain/                             # JVM specific
```

## Core Components

### 1. Main Entry Point (`Main.kt`)

The main application entry point handles:
- Command-line argument parsing
- Configuration loading
- Task initialization
- Exception handling

**Key Functions:**
- `main()` - Application bootstrap
- `mainTask()` - Launches DDNS and purge tasks
- `ddns()` - Core DDNS update logic
- `getIp()` - Fetches current public IP

### 2. Configuration System

Supports JSON5 and TOML formats with a two-level structure:

```json5
{
  "common": { /* Global defaults */ },
  "domains": [
    {
      "name": "example.com",
      "properties": { /* Override common settings */ }
    }
  ]
}
```

**Configuration Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `zoneId` | String | Cloudflare Zone ID |
| `authKey` | String | Cloudflare API Token |
| `v4` | Boolean | Enable IPv4 (A records) |
| `v6` | Boolean | Enable IPv6 (AAAA records) |
| `ttl` | Integer | DNS record TTL (seconds) |
| `proxied` | Boolean | Enable Cloudflare Proxy |
| `autoPurge` | Boolean | Auto-remove redundant records |
| `checkUrlV4/V6` | String | Custom IP detection URLs |
| `reInit` | Integer | Re-initialization interval |

### 3. DDNS Task Engine

**Workflow:**
1. **Initialize** - Fetch existing DNS records from Cloudflare
2. **Detect IP** - Call external API to get current public IP
3. **Compare** - Check if IP/proxy/TTL has changed
4. **Update** - Create or update DNS record via API
5. **Schedule** - Repeat based on TTL value

**Task Grouping:**
- Tasks are grouped by TTL for efficient scheduling
- IPv4 and IPv6 tasks are handled separately
- Different check URLs can be used per group

### 4. Cloudflare API Integration

**API Endpoints Used:**
- `GET /zones/{zoneId}/dns_records` - List/query records
- `POST /zones/{zoneId}/dns_records` - Create record
- `PUT /zones/{zoneId}/dns_records/{id}` - Update record
- `DELETE /zones/{zoneId}/dns_records/{id}` - Delete record

**Request Model:**
```kotlin
data class DnsRecordRequest(
    val type: String,      // "A" or "AAAA"
    val name: String,      // Domain name
    val content: String,   // IP address
    val ttl: Int,          // TTL in seconds
    val proxied: Boolean,  // Cloudflare proxy status
    val tags: List<String>,
    val comment: String
)
```

### 5. IP Detection

Uses external services to detect public IP:
- Default IPv4: `https://api4.ipify.org?format=text`
- Default IPv6: `https://api6.ipify.org?format=text`
- Custom URLs configurable via config file

## Build System

### Gradle Configuration (`build.gradle.kts`)

**Targets:**
- `linuxX64` - Native Linux x64 binary
- `linuxArm64` - Native Linux ARM64 binary
- `mingwX64` - Native Windows x64 binary
- `jvm` - Java JAR (JVM 17+)

**Build Tasks:**
- `multPackage` - Package all native targets
- `linuxX64CopyAndCompile` - Build Linux x64 binary
- `linuxArm64CopyAndCompile` - Build Linux ARM64 binary
- `mingwX64CopyAndCompile` - Build Windows x64 binary
- `jvmJar` - Build JVM JAR
- `nativeDockerBuildx` - Build native Docker image
- `jvmDockerBuildx` - Build JVM Docker image
- `github` - Full release build

## Deployment Options

### 1. Native Binary
```bash
./cf-ddns -c=config.json5
```

### 2. JVM (Java 17+)
```bash
java -jar -Xmx30m cf-ddns.jar -c=config.json5
```

### 3. Docker (Native)
```yaml
services:
  cf-ddns:
    image: selcarpa/cloudflare-ddns:latest
    volumes:
      - /path/to/config.json5:/cf-ddns/config.json5
    command: ["-c=/cf-ddns/config.json5"]
```

### 4. Docker (JVM)
```yaml
services:
  cf-ddns:
    image: selcarpa/cloudflare-ddns-jvm:latest
    volumes:
      - /path/to/config.json5:/cf-ddns/config.json5
    command: ["-c=/cf-ddns/config.json5"]
```

## Command-Line Interface

| Argument | Description |
|----------|-------------|
| `-c=<path>` | Specify configuration file |
| `-once` | Run once and exit (for cron) |
| `-gen` | Generate configuration template |
| `-purge` | Remove all DNS records and exit |
| `-debug` | Enable debug logging |
| `-help` | Show help message |
| `-file=<path>` | Output path for `-gen` |

## Data Flow

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Config     │────▶│  DdnsItem   │────▶│  Cloudflare │
│  File       │     │  (State)    │     │  API        │
└─────────────┘     └─────────────┘     └─────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │  IP Check   │
                    │  Service    │
                    └─────────────┘
```

## Key Design Patterns

1. **Coroutine-based Concurrency** - Uses Kotlin coroutines for async operations
2. **Dependency Injection** - Lazy-initialized HTTP client
3. **Strategy Pattern** - Pluggable IP detection URLs
4. **Builder Pattern** - Configuration building with defaults
5. **Retry Logic** - Automatic re-initialization on failure

## Security Considerations

- API tokens stored in configuration file (should be protected)
- Authorization header sanitized in logs
- No sensitive data in error messages
- HTTPS required for all API calls

## Performance Characteristics

- Memory footprint: ~30MB (JVM), <10MB (Native)
- Startup time: <1 second
- Network calls: Minimal (only on IP change or TTL expiry)
- CPU usage: Negligible (idle between checks)

## Future Enhancement Opportunities

1. Add webhook notifications for IP changes
2. Support for multiple DNS providers
3. Web UI for configuration management
4. Metrics/monitoring endpoint
5. Rate limiting protection
6. Backup/restore configuration
