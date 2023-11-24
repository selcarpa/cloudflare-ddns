# Cloudflare-ddns

## 介绍

这是一个简单的应用程序，用于将当前公共IP地址更新到Cloudflare DNS记录。适用场景：

- 设备使用动态公网ipv4访问互联网
- 设备没有公网ipv4，但是有公网ipv6
- 没有路由器的管理权限，但想要使用域名访问局域网中某个设备

项目提供：

1. native版本（含docker支持），支持Windows(x86)、Linux(x86)
2. java版本（含docker支持），支持jvm-17

## 快速开始

### native版本

1. 在文件所在同级目录创建文件cf-ddns-config.json，内容如下：
    ```json5
    {
        "common": {
          "zoneId": "",//填入cloudflare的zone id
          "authKey": "",//填入cloudflare的token
        },
        "domains": [
          {
            "name": "cf-ddns.tain.one"//用于ddns的域名
          }
        ]
      }
    ```
2. 前往[release](../../releases/latest)下载当前系统最新版本，重命名为cf-ddns
3. 运行
   ```shell
   ./cf-ddns -c=cf-ddns-config.json
   ```

<details>

<summary>使用Docker启动</summary>

#### native-docker版本

1. 同native版本第一步
2. 创建docker-compose.yml文件
   ```yaml
   ---
   version: "3"
   services:
     cf-ddns:
       image: selcarpa/cloudflare-ddns:latest
       container_name: cf-ddns
       volumes:
         - /path/to/config.json5:/app/config.json5  # 挂载配置文件，注意，/path/to/config.json5需要替换为实际路径
       restart: unless-stopped # 重启策略
       command: ["-c=/app/config.json5"] # 启动命令
   ```
3. 启动
   ```shell
    docker-compose up -d
    ```

</details>

<details>

<summary>另外提供Java版本，当前版本不支持那么多的系统架构，仅作预览</summary>

### java版本

1. 同native版本第一步，创建cf-ddns-config.json
2. 前往[release](../../releases/latest)下载当前最新jar版本，重命名为cf-ddns.jar
3. 运行
   ```shell
   java -jar cf-ddns.jar -c=cf-ddns-config.json
   ```

### java-docker版本

1. 同native版本第一步
2. 创建docker-compose.yml文件
   ```yaml
   ---
   version: "3"
   services:
     cf-ddns:
       image: selcarpa/cloudflare-ddns-jvm:latest
       container_name: cf-ddns
       volumes:
         - /path/to/config.json5:/app/config.json5  # 挂载配置文件，注意，/path/to/config.json5需要替换为实际路径
       restart: unless-stopped # 重启策略
       command: ["-c=/app/config.json5"] # 启动命令
   ```
3. 同native-docker版本第三步

</details>

## 配置文件

配置文件支持toml和json/json5格式

```json5
//完整例:
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

### 配置文件最顶层

| 字段名     | 类型         | 必填 | 说明   |
|---------|------------|----|------|
| domains | 数组(Domain) | 是  | 域名配置 |
| common  | Properties | 是  | 通用配置 |

### Domain

| 字段名        | 类型         | 必填 | 说明                                     |
|------------|------------|----|----------------------------------------|
| name       | 字符串        | 是  | 域名                                     |
| properties | Properties | 否  | 域名配置，如果存在，会覆盖common中的配置，否则使用common中的配置 |

### Properties

| 字段名        | 类型   | 必填 | 说明                                               |
|------------|------|----|--------------------------------------------------|
| zoneId     | 字符串  | 是  | cloudflare的zone id                               |
| authKey    | 字符串  | 是  | cloudflare的token                                 |
| checkUrlV4 | 字符串  | 否  | 检查ipv4的url，默认为https://api4.ipify.org?format=text |
| checkUrlV6 | 字符串  | 否  | 检查ipv6的url，默认为https://api6.ipify.org?format=text |
| v4         | 布尔类型 | 否  | 是否启用ipv4，默认为true                                 |
| v6         | 布尔类型 | 否  | 是否启用ipv6，默认为false                                |
| ttl        | 整型数字 | 否  | DNS记录的ttl，默认为1分钟                                 |
| autoPurge  | 布尔类型 | 否  | 是否自动清理DNS记录，默认为false                             |
| proxied    | 布尔类型 | 否  | 是否启用cloudflare的代理，默认为false                       |
| ttlCheck   | 布尔类型 | 否  | 是否启用ttl检查，默认为false                               |

