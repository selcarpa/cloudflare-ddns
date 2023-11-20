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

1. 前往[release](./releases/latest)下载当前系统最新版本，重命名为cf-ddns
2. 在文件所在同级目录创建文件cf-ddns-config.json，内容如下：
    ```json5
    {
        "common": {
          "zoneId": "",//填入上面的zone id
          "authKey": "",//填入上面的token
          "v4": false,//是否更新ipv4
          "v6": false,//是否更新ipv6
          "ttl": 300 //ttl，同时用于缓存时间和dns记录检测的间隔
        },
        "domains": [
          {
            "name": "cd1.tain.one",//用于ddns的域名
            "proxied": true //是否开启cloudflare的代理
          }
        ]
      }
    ```
3. 运行
   ```shell
   ./cf-ddns -c=cf-ddns-config.json
   ```

### java版本

1. 前往[release](./releases/latest)下载当前最新jar版本，重命名为cf-ddns.jar
2. 在文件所在同级目录创建文件cf-ddns-config.json，内容如下：
    ```json5
    {
        "common": {
          "zoneId": "",//填入上面的zone id
          "authKey": "",//填入上面的token
          "v4": false,//是否更新ipv4
          "v6": false,//是否更新ipv6
          "ttl": 300 //ttl，同时用于缓存时间和dns记录检测的间隔
        },
        "domains": [
          {
            "name": "cd1.tain.one",//用于ddns的域名
            "proxied": true //是否开启cloudflare的代理
          }
        ]
      }
    ```
3. 运行
   ```shell
   java -jar cf-ddns.jar -c=cf-ddns-config.json
   ```

## 配置文件

配置文件支持toml和json格式，本文档以json格式为例。


