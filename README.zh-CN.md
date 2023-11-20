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

## 配置文件

配置文件支持toml和json格式，本文档以json格式为例。

配置文件构成：

