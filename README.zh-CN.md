# Cloudflare-ddns

## 介绍

这是一个简单的应用程序，用于将当前公共IP地址更新到Cloudflare DNS记录。适用场景：

- 设备使用动态公网ipv4访问互联网
- 设备没有公网ipv4，但是有公网ipv6
- 没有路由器的管理权限，但想要使用域名访问局域网中某个设备

项目提供：

1. native版本（含docker支持），支持Windows(x86)、Linux(x86)~~、Linux(Arm)等待依赖更新中~~
2. java版本（含docker支持），支持jvm-17兼容平台

功能提供：

- 支持ipv4/ipv6
- 超级快速开始并且生成配置文件
- 详细的自定义配置，包含：
    - 多域名支持，跨zone支持，跨账号支持
    - 自定义检查ipv4/ipv6地址的url
- 快捷功能
    - 一次性操作
    - 清理DNS记录

## 快速开始

### native版本

1. 前往[release](../../releases/latest)下载当前系统最新版本，重命名为cf-ddns(Windows为cf-ddns.exe)
2. 当前路径下打开终端，运行
   ```shell
    #Linux
    ./cf-ddns -gen -zoneId=xxxXXxxXXXxzoneIdxxxXXXx -authKey=XXXxauthKeyxxxXXXx -domain=ex.example.com -v4=true -v6=false
    #Windows
    ./cf-ddns.exe -gen -zoneId=xxxXXxxXXXxzoneIdxxxXXXx -authKey=XXXxauthKeyxxxXXXx -domain=ex.example.com -v4=true -v6=false
   ```
3. 观察Cloudflare的DNS记录是否更新

<details>

<summary>使用Docker启动</summary>

### native-docker版本

1. 创建docker-compose.yml文件
   ```yaml
   ---
   services:
     cf-ddns:
       image: selcarpa/cloudflare-ddns:latest
       # network_mode: "host" # 如果-v6为true，需要使用host网络模式
       container_name: cf-ddns
        # environment: # 在 cf-ddns 的默认注释中，记录了更新时间，可以在这里设置时区
        #  - TZ=Asia/Shanghai
       restart: unless-stopped # 重启策略
       command: ["-gen","-zoneId=xxxXXxxXXXxzoneIdxxxXXXx","-authKey=XXXxauthKeyxxxXXXx","-domain=ex.example.com","-v4=true","-v6=false"] # 启动命令
   ```
2. 启动
   ```shell
    docker-compose up -d
    ```

</details>

<details>

<summary>另外提供Java版本</summary>

### java版本

1. 前往[release](../../releases/latest)下载当前最新jar版本，重命名为cf-ddns.jar
2. 运行
   ```shell
   java -jar -Xmx30m cf-ddns.jar -gen -zoneId=xxxXXxxXXXxzoneIdxxxXXXx -authKey=XXXxauthKeyxxxXXXx -domain=ex.example.com -v4=true -v6=false
   ```

### java-docker版本

1. 创建docker-compose.yml文件
   ```yaml
   ---
   services:
     cf-ddns:
       image: selcarpa/cloudflare-ddns-jvm:latest
       # network_mode: "host" # 如果-v6为true，需要使用host网络模式
       container_name: cf-ddns
        # environment: # 在 cf-ddns 的默认注释中，记录了更新时间，可以在这里设置时区
        #  - TZ=Asia/Shanghai
       restart: unless-stopped # 重启策略
       command: ["-gen","-zoneId=xxxXXxxXXXxzoneIdxxxXXXx","-authKey=XXXxauthKeyxxxXXXx","-domain=ex.example.com","-v4=true","-v6=false"] # 启动命令
   ```
2. 启动
   ```shell
    docker-compose up -d
    ```

</details>

## 更多功能

### 自定义启动cloudflare-ddns

通过配置文件启动Cloudflare-ddns，可以实现更多功能，如：

- 多域名支持
- 自定义检查ipv4/ipv6地址的url
- 自定义ttl
- 自动清理DNS记录
- cloudflare代理支持
- ttl检查

#### 编写配置文件

配置文件支持toml和json/json5格式，此处以json5为示例

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

##### 配置文件最顶层

| 字段名     | 类型         | 必填 | 说明   |
|---------|------------|----|------|
| domains | 数组(Domain) | 是  | 域名配置 |
| common  | Properties | 是  | 通用配置 |

##### Domain

| 字段名        | 类型         | 必填 | 说明                                               |
|------------|------------|----|--------------------------------------------------|
| name       | 字符串        | 是  | 域名                                               |
| properties | Properties | 否  | 域名配置，如果存在，会覆盖common中的配置，如果此处部任何配置为空，使用common中的配置 |

##### Properties

| 字段名        | 类型   | 必填 | 说明                                                                                                           |
|------------|------|----|--------------------------------------------------------------------------------------------------------------|
| zoneId     | 字符串  | 是  | cloudflare的zone id                                                                                           |
| authKey    | 字符串  | 是  | cloudflare的authKey                                                                                           |
| checkUrlV4 | 字符串  | 否  | 检查ipv4的url，默认为https://api4.ipify.org?format=text                                                             |
| checkUrlV6 | 字符串  | 否  | 检查ipv6的url，默认为https://api6.ipify.org?format=text                                                             |
| v4         | 布尔类型 | 否  | 是否启用ipv4，默认为true                                                                                             |
| v6         | 布尔类型 | 否  | 是否启用ipv6，默认为false                                                                                            |
| ttl        | 整型数字 | 否  | DNS记录的ttl，秒数，默认为300                                                                                          |
| autoPurge  | 布尔类型 | 否  | 是否自动清理DNS记录，默认为false                                                                                         |
| proxied    | 布尔类型 | 否  | 是否启用cloudflare的代理，默认为false                                                                                   |
| comment    | 字符串  | 否  | 注释，用于显示在cloudflare的面板上，不会对功能有任何影响，默认为"cf-ddns auto update"，如果需要不显示，请手动覆盖                                     |
| ttlCheck   | 布尔类型 | 否  | 是否启用ttl检查，默认为false                                                                                           |
| reInit     | 整型数字 | 否  | 多少次任务后，重新进行初始化域名基本信息，默认为300除以ttl值，如果为0则不会重新初始化，在进行该次数的任务之后，重新检查cloudflare上关于此域名的情况，避免由有其他操作删除了该域名，导致无法自动重新新建 |

#### 通过配置文件启动cloudflare-ddns

### native版本

```shell
# Linux
./cf-ddns -c=config.json5
# Windows
./cf-ddns.exe -c=config.json5
```

<details>
<summary>Docker版本</summary>

```yaml
     services:
       cf-ddns:
         image: selcarpa/cloudflare-ddns:latest
         # network_mode: "host" # 如果有ipv6的ddns项目，需要使用host网络模式
         container_name: cf-ddns
         # environment: # 在 cf-ddns 的默认注释中，记录了更新时间，可以在这里设置时区
         #  - TZ=Asia/Shanghai
         volumes:
           - /path/to/config.json5:/cf-ddn/config.json5  # 挂载配置文件，注意，/path/to/config.json5需要替换为实际路径
         restart: unless-stopped # 重启策略
         command: [ "-c=/cf-ddn/config.json5" ] # 启动命令
```

</details>

### java版本

```shell
java -jar -Xmx30m cf-ddns.jar -c=config.json5
```

<details>
<summary>Docker版本</summary>

```yaml
     services:
       cf-ddns:
         image: selcarpa/cloudflare-ddns-jvm:latest
         # network_mode: "host" # 如果有ipv6的ddns项目，需要使用host网络模式
         container_name: cf-ddns
         # environment: # 在 cf-ddns 的默认注释中，记录了更新时间，可以在这里设置时区
         #  - TZ=Asia/Shanghai
         volumes:
           - /path/to/config.json5:/cf-ddns/config.json5  # 挂载配置文件，注意，/path/to/config.json5需要替换为实际路径
         restart: unless-stopped # 重启策略
         command: [ "-c=/cf-ddn/config.json5" ] # 启动命令
```

</details>

### 启动参数实现单次进行操作的功能

- `-once` 仅执行一次，不会启动定时任务
- `-purge` 清理DNS记录，不会启动定时任务
- `-debug` 开启debug模式，会输出更多日志
- `-gen` 生成配置文件模板，此为上文中快速启动的命令，需要配合以下参数使用
    - `-zoneId` cloudflare的zone id，必填
    - `-authKey` cloudflare的authKey，必填
    - `-domain` 域名，必填
    - `-v4` 是否启用ipv4，可选，默认为true
    - `-v6` 是否启用ipv6，可选，默认为false
