# Cloudflare-ddns

[简体中文](./README.zh-CN.md)

## introduce

This is a simple application for updating the current public IP address to Cloudflare DNS records. Applicable scene:

- The device uses dynamic public network IPv4 to access the Internet
- The device does not have public IPv4, but has public IPv6
- Do not have administrative rights to the router, but want to use a domain name to access a device on the LAN

The project provides:

1. Native version (including docker support), supports Windows (x86), Linux (x86)~~, Linux (Arm), waiting for dependency updates~~
2. Java version (including docker support), supports jvm-17 compatible platform

Features provided:

- Support ipv4/ipv6
- Super quick to start and generate configuration files
- Detailed custom configuration, including:
    - Multi-domain name support, cross-zone support, cross-account support
    - Customize the URL for checking ipv4/ipv6 addresses
- Quick functions
    - One-time operation
    - Clean DNS records

## Quick start

### native version

1. Go to [release](../../releases/latest) to download the latest version of the current system and rename it to cf-ddns (Windows: cf-ddns.exe)
2. Open the terminal under the current path and run
    ```shell
    #Linux
    ./cf-ddns -gen -zoneId=xxxXXxxXXXxzoneIdxxxXXXx -authKey=XXXxauthKeyxxxXXXx -domain=ex.example.com -v4=true -v6=false
    #Windows
    ./cf-ddns.exe -gen -zoneId=xxxXXxxXXXxzoneIdxxxXXXx -authKey=XXXxauthKeyxxxXXXx -domain=ex.example.com -v4=true -v6=false
    ```
3. Observe whether DNS records in Cloudflare are updated

<details>

<summary>Start using Docker</summary>

### native-docker version

1. Create docker-compose.yml file
    ```yaml
    services:
      cf-ddns:
        image: selcarpa/cloudflare-ddns:latest
        network_mode: "host"
        container_name: cf-ddns
        restart: unless-stopped # Restart strategy
        command: ["-gen","-zoneId=xxxXXxxXXXxzoneIdxxxXXXx","-authKey=XXXxauthKeyxxxXXXx","-domain=ex.example.com","-v4=true","-v6=false"] # Start command
    ```
2. Start
    ```shell
     docker-compose up -d
     ```

</details>

<details>

<summary>A Java version is also provided.</summary>

### java version

1. Go to [release](../../releases/latest) to download the latest jar version and rename it to cf-ddns.jar
2. run
    ```shell
    java -jar -Xmx30m cf-ddns.jar -gen -zoneId=xxxXXxxXXXxzoneIdxxxXXXx -authKey=XXXxauthKeyxxxXXXx -domain=ex.example.com -v4=true -v6=false
    ```

### java-docker version

1. Create docker-compose.yml file
    ```yaml
    services:
      cf-ddns:
        image: selcarpa/cloudflare-ddns-jvm:latest
        network_mode: "host"
        container_name: cf-ddns
        restart: unless-stopped # Restart strategy
        command: ["-gen","-zoneId=xxxXXxxXXXxzoneIdxxxXXXx","-authKey=XXXxauthKeyxxxXXXx","-domain=ex.example.com","-v4=true","-v6=false"] # Start command
    ```
2. Start
    ```shell
     docker-compose up -d
     ```

</details>

## More features

### Custom start cloudflare-ddns

Starting Cloudflare-ddns through the configuration file can achieve more functions, such as:

- Multiple domain names support
- Customize the URL for checking ipv4/ipv6 addresses
- Custom ttl
- Automatically clean DNS records
- cloudflare proxy support
- ttl check

#### Write configuration file

The configuration file supports toml and json/json5 formats. Here json5 is used as an example.

```json5
//Complete example:
{
  "common": {
    "zoneId": "xxxXXxxXXXxzoneIdxxxXXXx",
    "authKey": "XXXxauthKeyxxxXXXx",
    "v4": false,
    "v6": true,
    "ttl": 300,
    "ttlCheck": true,
    "checkUrlV4": "https://w4.tain.one/",
    "checkUrlV6": "https://w6.tain.one/",
    "autoPurge": true,
    "proxied": true,
    "comment": "cf-ddns auto update",
    "reInit": 5
  },
  "domains": [
    {
      "name": "ex.example.com",
      "properties": {
        "zoneId": "xxxXXxxXXXxzoneIdxxxXXXx",
        "authKey": "XXXxauthKeyxxxXXXx",
        "v4": true,
        "v6": false,
        "ttl": 300,
        "ttlCheck": true,
        "checkUrlV4": "https://w4.tain.one/",
        "checkUrlV6": "https://w6.tain.one/",
        "autoPurge": true,
        "proxied": true,
        "comment": "cf-ddns auto update",
        "reInit": 5
      }
    }
  ]
}
```

##### The top level of the configuration file

| Field name | Type           | Required | Description               |
|------------|----------------|----------|---------------------------|
| domains    | Array (Domain) | Yes      | Domain name configuration |
| common     | Properties     | Yes      | Common configuration      |

##### Domain

| Field name | Type       | Required | Description                                                                                                                                              |
|------------|------------|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| name       | String     | yes      | domain name                                                                                                                                              |
| properties | Properties | No       | Domain name configuration, if it exists, will overwrite the configuration in common. If any configuration here is empty, use the configuration in common |

##### Properties

| Field name | Type           | Required | Description                                                                                                                                                                                                     |
|------------|----------------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| zoneId     | String         | yes      | zone id from cloudflare                                                                                                                                                                                         |
| authKey    | String         | yes      | authKey from cloudflare                                                                                                                                                                                         |
| checkUrlV4 | String         | No       | Check the url of ipv4, the default is https://api4.ipify.org?format=text                                                                                                                                        |
| checkUrlV6 | String         | No       | Check the url of ipv6, the default is https://api6.ipify.org?format=text                                                                                                                                        |
| v4         | Boolean type   | No       | Whether to enable ipv4, the default is true                                                                                                                                                                     |
| v6         | Boolean type   | No       | Whether to enable ipv6, the default is false                                                                                                                                                                    |
| ttl        | Integer number | No       | DNS record ttl, seconds, default is 300                                                                                                                                                                         |
| autoPurge  | Boolean type   | No       | Whether to automatically clean up DNS records, the default is false                                                                                                                                             |
| proxied    | Boolean type   | No       | Whether to enable cloudflare proxy, the default is false                                                                                                                                                        |
| comment    | String         | No       | Comments, which are used to display on cloudflare dashboard and do not have any impact on functionality, default to "cf-ddns auto update", if you need not to display, please manually override to empty string |
| ttlCheck   | Boolean type   | No       | Whether to enable ttl check, the default is false                                                                                                                                                               |
| reInit     | Integer number | No       | After the number of tasks, re-check the record of the domain name on Cloudflare, to avoid deleting the domain name due to other operations, resulting in the failure to automatically re-create the domain name |

#### Start cloudflare-ddns

### native version

```shell
#Linux
./cf-ddns -c=config.json5
# Windows
./cf-ddns.exe -c=config.json5
```

<details>
<summary>Docker version</summary>

```yaml
    services:
      cf-ddns:
        image: selcarpa/cloudflare-ddns:latest
        network_mode: "host"
        container_name: cf-ddns
        volumes:
          - /path/to/config.json5:/cf-ddn/config.json5 # Mount the configuration file. Note that /path/to/config.json5 needs to be replaced with the actual path.
        restart: unless-stopped # Restart strategy
        command: [ "-c=/cf-ddn/config.json5" ] # Start command
```

</details>

### java version

```shell
java -jar -Xmx30m cf-ddns.jar -c=config.json5
```

<details>
<summary>Docker version</summary>

```yaml
     services:
       cf-ddns:
         image: selcarpa/cloudflare-ddns-jvm:latest
         network_mode: "host"
         container_name: cf-ddns
         volumes:
           - /path/to/config.json5:/cf-ddns/config.json5 # Mount the configuration file. Note that /path/to/config.json5 needs to be replaced with the actual path.
         restart: unless-stopped # Restart strategy
         command: [ "-c=/cf-ddn/config.json5" ] # Start command
```

</details>

### Startup parameters realize the function of single operation

- `-once` will only be executed once and will not start the scheduled task
- `-purge` cleans DNS records and does not start scheduled tasks
- `-debug` turns on debug mode and will output more logs
- `-gen` generates a configuration file template. This is the quick start command above and needs to be used with the following parameters.
   - `-zoneId` zone id from Cloudflare, required
   - `-authKey` authKey from Cloudflare, required
   - `-domain` domain name, required
   - `-v4` Whether to enable ipv4, optional, default is true
   - `-v6` Whether to enable ipv6, optional, default is false
