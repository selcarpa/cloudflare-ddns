# Cloudflare-ddns

[简体中文](./README.zh-CN.md)

## introduce

This is a simple application for updating the current public IP address to Cloudflare DNS records. Applicable scene:

- The device uses dynamic public network IPv4 to access the Internet
- The device does not have public IPv4, but has public IPv6
- Do not have administrative rights to the router, but want to use a domain name to access a device on the LAN

The project provides:

1. Native version (including docker support), supports Windows (x86), Linux (x86)
2. Java version (including docker support), supports jvm-17

## Quick start

### native version

1. Create the file cf-ddns-config.json in the same directory as the file, with the following content:
     ```json5
     {
         "common": {
           "zoneId": "",//Fill in the zone id of cloudflare
           "authKey": "",//Fill in the cloudflare token
         },
         "domains": [
           {
             "name": "cf-ddns.tain.one"//Domain name used for ddns
           }
         ]
       }
     ```
2. Go to [release](../../releases/latest) to download the latest version of the current system and rename it to cf-ddns
3. Run
    ```shell
    ./cf-ddns -c=cf-ddns-config.json
    ```

<details>

<summary>Start using Docker</summary>

#### native-docker version

1. Same as the first step of the native version
2. Create docker-compose.yml file
   ```yaml
   ---
   version: "3"
   services:
     cf-ddns:
       image: selcarpa/cloudflare-ddns:latest
       container_name: cf-ddns
       volumes:
         - /path/to/config.json5:/app/config.json5  # Mount the configuration file. Note that /path/to/config.json5 needs to be replaced with the actual path.
       restart: unless-stopped # Restart strategy
       command: ["-c=/app/config.json5"] # Start command
    ```
3. Start
    ```shell
     docker-compose up -d
     ```

</details>

<details>

<summary>A Java version is also provided. The current version does not support so many system architectures and is only a preview</summary>

### java version

1. Same as the first step in the native version, create cf-ddns-config.json
2. Go to [release](../../releases/latest) to download the latest jar version and rename it to cf-ddns.jar
3. Run
    ```shell
    java -jar cf-ddns.jar -c=cf-ddns-config.json
    ```

### java-docker version

1. Same as the first step of the native version
2. Create docker-compose.yml file
    ```yaml
   ---
   version: "3"
   services:
     cf-ddns:
       image: selcarpa/cloudflare-ddns-jvm:latest
       container_name: cf-ddns
       volumes:
         - /path/to/config.json5:/app/config.json5  # Mount the configuration file. Note that /path/to/config.json5 needs to be replaced with the actual path.
       restart: unless-stopped # Restart strategy
       command: ["-c=/app/config.json5"] # Start command
    ```
3. Same as the third step of native-docker version

</details>

## Configuration file

The configuration file supports toml and json/json5 formats

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
      }
    }
  ]
}
```

### The top level of the configuration file

| Field name | Type           | Required | Description               |
|------------|----------------|----------|---------------------------|
| domains    | Array (Domain) | Yes      | Domain name configuration |
| common     | Properties     | Yes      | Common configuration      |

### Domain

| Field name | Type       | Required | Description                                                                                                                                              |
|------------|------------|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| name       | String     | yes      | domain name                                                                                                                                              |
| properties | Properties | No       | Domain name configuration, if it exists, will overwrite the configuration in common. If any configuration here is empty, use the configuration in common |

### Properties

| Field name | Type           | Required | Description                                                              |
|------------|----------------|----------|--------------------------------------------------------------------------|
| zoneId     | String         | yes      | zone id from cloudflare                                                  |
| authKey    | String         | yes      | token from cloudflare                                                    |
| checkUrlV4 | String         | No       | Check the url of ipv4, the default is https://api4.ipify.org?format=text |
| checkUrlV6 | String         | No       | Check the url of ipv6, the default is https://api6.ipify.org?format=text |
| v4         | Boolean type   | No       | Whether to enable ipv4, the default is true                              |
| v6         | Boolean type   | No       | Whether to enable ipv6, the default is false                             |
| ttl        | Integer number | No       | DNS record ttl, seconds, default is 300                                  |
| autoPurge  | Boolean type   | No       | Whether to automatically clean up DNS records, the default is false      |
| proxied    | Boolean type   | No       | Whether to enable cloudflare proxy, the default is false                 |
| ttlCheck   | Boolean type   | No       | Whether to enable ttl check, the default is false                        |
