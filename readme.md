# MQTTX Project

![license](https://img.shields.io/github/license/tensorflow/tensorflow.svg) ![language](https://img.shields.io/badge/language-java-orange.svg)

中文 | [English](./readme_en.md)

- [1 介绍](#1-介绍)
    - [1.1 快速开始](#11-快速开始)
    - [1.2 项目依赖](#12-项目依赖)
    - [~~1.3  线上实例~~](#13-线上实例)
- [2 架构](#2-架构)
    - [2.1 目录结构](#21-目录结构)
- [3 docker 启动](#3-docker-启动)
- [4 功能说明](#4-功能说明)
    - [4.1 qos 支持](#41-qos-支持)
    - [4.2 topicFilter 支持](#42-topicfilter-支持)
    - [4.3 集群支持](#43-集群支持)
    - [4.4 ssl 支持](#44-ssl-支持)
    - [4.5 topic 安全支持](#45-topic-安全支持)
    - [4.6 共享主题支持](#46-共享主题支持)
    - [4.7 websocket 支持](#47-websocket-支持)
    - [4.8 系统主题](#48-系统主题)
      - [4.8.1 状态主题](#481-状态主题)
      - [4.8.2 功能主题](#482-功能主题)
    - [4.9 消息桥接支持](#49-消息桥接支持)
    - [4.10 主题限流支持](#410-主题限流支持)
    - [4.11 消息持久化支持](#411-消息持久化支持)
    - [4.12 基础认证支持](#412-基础认证支持)
- [5 开发者说](#5-开发者说)
- [6 附表](#6-附表)
    - [6.1 配置项](#61-配置项)

## 1 介绍

`Mqttx` 基于 [MQTT v3.1.1](http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html) 协议开发，旨在提供 ***易于使用*** 且 ***性能优越*** 的 **mqtt broker**。

注意：分支 `v1.2` 要求 ***JDK17***, 其它分支要求 ***JDK8***

关联项目: [Mqttx-Client](https://github.com/Amazingwujun/mqttx-client) 实现&使用极为简单的 mqttv3.1.1 客户端.

### 1.1 快速开始

> 想通过 docker 快速体验？见 [docker 启动](#3-docker-启动)

1. 打包
    - 开发模式：
        1. 启动 `redis` 实例
        2. 运行 `mvnw -P dev -DskipTests=true clean package`
2. 运行

    1. 运行命令：`java -jar mqttx-1.0.5.BETA.jar`

图例：

<img src="https://s1.ax1x.com/2020/09/27/0kJp3F.gif" alt="快速开始" style="zoom: 80%;" />

### 1.2 项目依赖

- [x] **Redis**： 集群消息、消息持久化
- [x] **Kafka**：桥接消息支持，集群消息（可选功能）

其它说明：

1. 项目使用了 **lombok**，使用 **ide** 请安装对应的插件

> 开发工具建议使用 [Intellij IDEA](https://www.jetbrains.com/idea/) :blush:
>
> 举例：`idea` 需要安装插件 `Lombok`, `settings > Build,Execution,Deployment > Compiler > Annotation Processor` 开启 `Enable annotation processing`

### ~~1.3 线上实例~~

云服务到期，实例已经无法访问，有朋友赞助吗/(ㄒoㄒ)/~~

> 云端部署了一个 `mqttx` 单例服务，可供功能测试：
>
> 1. 不支持 `ssl`
> 2. 开启了 `websocket`, 可通过 http://ws.tool.tusk.link/ 测试，仅需将域名修改为：`119.45.158.51`(端口、地址不变)
> 3. 支持共享订阅功能
> 4. 部署版本 `v1.0.6.RELEASE`
>
> ![websocket](https://s1.ax1x.com/2020/09/05/wV578J.png)



## 2 架构

`mqttx`支持客户端认证、topic 发布/订阅鉴权功能，如果需要配套使用，建议的架构如下图：

![架构图](https://s1.ax1x.com/2020/07/28/ak6KAO.png)

> 客户认证服务由使用者自行实现

内部实现框架关系(仅列出关键项)：

![ak6mB6.png](https://s1.ax1x.com/2020/07/28/ak6mB6.png)

### 2.1 目录结构

```
├─java
│  └─com
│      └─jun
│          └─mqttx
│              ├─broker         # mqtt 协议实现及处理包
│              │  ├─codec       # 编解码
│              │  └─handler     # 消息处理器（pub, sub, connn, etc）
│              ├─config         # 配置，主要是 bean 声明
│              ├─constants      # 常量
│              ├─consumer       # 集群消息消费者
│              ├─entity         # 实体类
│              ├─exception      # 异常类
│              ├─service        # 业务服务（用户认证, 消息存储等）接口
│              │  └─impl        # 默认实现
│              └─utils          # 工具类
└─resources                     # 资源文件（application.yml 在此文件夹）
    ├─META-INF                  # spring-configuration 辅助配置说明
    └─tls                       # ca 存放地址
```

## 3 docker 启动

镜像已上传至  **docker-hub** , 访问：[fantasywujun/mqttx - Docker Hub](https://hub.docker.com/r/fantasywujun/mqttx) 全部镜像

docker 环境安装好后，执行 `docker-compose -f ./docker-compose.yml up` 启动, 效果见下图：

![y3R3tI.md.png](https://s3.ax1x.com/2021/02/04/y3R3tI.md.png)

| Docker Pull Command                    | 说明                                |
|----------------------------------------|-----------------------------------|
| `docker pull fantasywujun/mqttx:1.2.0` | 基于 `jdk17.0.1` 的 `mqttx:1.2.0` 版本 |
| `docker pull fantasywujun/mqttx:1.2.1` | 基于 `jdk17.0.1` 的 `mqttx:1.2.1` 版本 |
| `docker pull fantasywujun/mqttx:1.2.2` | 基于 `jdk17.0.1` 的 `mqttx:1.2.2` 版本 |
| `docker pull fantasywujun/mqttx:1.2.3` | 基于 `jdk17.0.1` 的 `mqttx:1.2.3` 版本 |

**docker-compose** 文件内容：

```yaml
version: "2"
services:
  redis:
    container_name: redis-for-mqttx
    image: redis
  mqttx:
    container_name: mqttx
    image: fantasywujun/mqttx:1.2.2
    environment:
      mqttx.max-bytes-in-message: 10485760
      mqttx.web-socket.enable: false
    ports:
      - 1883:1883
```



## 4 功能说明

#### 4.1 qos 支持

| qos0 | qos1 | qos2 |
| ---- | ---- | ---- |
| 支持 | 支持 | 支持 |

为支持 `qos1、qos2`，引入 `redis` 作为持久层，这部分已经封装成接口，可自行替换实现（比如采用 `mysql`）。

#### 4.2 topicFilter 支持

1. 支持多级通配符 `#`与单级通配符 `+`
2. 不支持以 `/`结尾的topic，比如 `a/b/`，请改为 `a/b`。
3. 其它规则见 ***[mqtt v3.1.1](http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html) 4.7 Topic Names and Topic
   Filters***

> **mqttx** 仅对订阅 topicFilter 进行校验，publish 的 topic 是没有做合法性检查的，可通过开启 [4.5 topic 安全支持](#45-topic-安全支持) 限制客户端可发布的 topic。

举例：

| topicFilter  | match topics                  |
| ------------ | ----------------------------- |
| `/a/b/+`     | `/a/b/abc`,`/a/b/test`        |
| `a/b/#`      | `a/b`, `a/b/abc`, `a/b/c/def` |
| `a/+/b/#`    | `a/nani/b/abc`                |
| `/+/a/b/+/c` | `/aaa/a/b/test/c`             |

校验工具类为：`com.jun.mqttx.utils.TopicUtils`

#### 4.3 集群支持

`mqttx` 依赖消息中间件分发消息实现集群功能，目前支持的中间件：

- [x] `Kafka`：可选配置。为了更好的性能，推荐 kafka 作为集群消息分发器
- [x] `Redis`：默认配置

实现原理如下图：

![ak6nHK.png](https://s1.ax1x.com/2020/07/28/ak6nHK.png)

1. `mqttx.cluster.enable`：功能开关，默认 `false`
2. `mqttx.cluster.type`: 消息中间件类型，默认 `redis`

注意事项：

1. `v1.0.5.RELEASE` 之前的版本集群功能存在 bug，无法使用。

2. 如需使用 `kafka` 实现集群消息，需要手动修改配置 `application-*.yml`, 可参考 `application-dev.yml` 中的配置示例 ***3. kafka 集群***。

#### 4.4 ssl 支持

开启 ssl 你首先应该有了 *ca*(自签名或购买)，然后修改 `application.yml` 文件中几个配置：

1. `mqttx.ssl.enable`：功能开关，默认 `false`，同时控制 `websocket` 与 `socket`
2. `mqttx.ssl.key-store-location`：keystore 地址，基于 `classpath`
3. `mqttx.ssl.key-store-password`：keystore 密码
4. `mqttx.ssl.key-store-type`：keystore 类别，如 `PKCS12`
5. `mqttx.ssl.client-auth`：服务端是否需要校验客户端证书，默认 `NONE`

> `resources/tls` 目录中的 `mqttx.keystore` 仅供测试使用, 密码: `123456`
>
> 证书加载工具类：`com/jun/mqttx/utils/SslUtils.java`

#### 4.5 topic 安全支持

为了对 client 订阅 topic 进行限制，加入**topic 订阅&发布鉴权**机制:

1. `mqttx.enable-topic-sub-pub-secure`: 功能开关，默认 `false`

2. broker 收到 conn 报文后，会抓取 `{clientId, username, password}` 发起请求给 `mqttx.auth.url` , 该接口返回对象中含有 `authorizedSub,authorizedPub` 存储 **client** 被授权订阅及发布的 `topic` 列表。

   详见 [4.12 基础认证支持](#412-基础认证支持) 

3. broker 在消息订阅及发布都会校验客户端权限

支持的主题类型：

- [x] 普通主题
- [x] 共享主题
- [x] 系统主题

#### 4.6 共享主题支持

共享订阅是协议 `mqtt5` 规定的内容，**`MQTTX`** 参考协议标准实现。

1. 格式: `$share/{ShareName}/{filter}`, `$share` 为前缀, `ShareName` 为共享订阅名, `filter` 就是非共享订阅主题过滤器。
2. 支持如下两种消息分发规则
   1. `round`: 轮询
   2. `random`: 随机
3. 支持客户端订阅按 `ShareName` 分组订阅.
3. 详细内容请参考协议 [MQTT Version 5.0 (oasis-open.org)](https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901250) 

下图展示了共享主题与常规主题之间的差异:

![share-topic](https://s1.ax1x.com/2020/09/22/wXddnU.png)

`msg-a` 消息分发策略取决于配置项 `mqttx.share-topic.share-sub-strategy`

可以配合 `cleanSession = 1` 的会话，共享主题的客户端断开连接后会被服务端移除订阅，这样共享主题的消息只会分发给在线的客户端。

***CleanSession*** 介绍：`mqtt3.1.1` 协议规定当 `cleanSession = 1` 时，连接断开后与会话相关联的所有状态（不含 `retained` 消息）都会被删除（`mqtt5`
增加了会话超时设置，感兴趣的同学可以了解一下）。
`mqttx v1.0.5.BETA` 版本后(含)，`cleanSession = 1` 的会话消息保存在内存中，具备极高的性能.

> If CleanSession is set to 1, the Client and Server **MUST** discard any previous Session and start a new one. This Session lasts as long as the Network Connection. State data associated with this Session **MUST NOT** be reused in any subsequent Session [MQTT-3.1.2-6].
>
> The Session state in the Client consists of:
>
> - QoS 1 and QoS 2 messages which have been sent to the Server, but have not been completely acknowledged.
> - QoS 2 messages which have been received from the Server, but have not been completely acknowledged.
>
> The Session state in the Server consists of:
>
> - The existence of a Session, even if the rest of the Session state is empty.
> - The Client’s subscriptions.
> - QoS 1 and QoS 2 messages which have been sent to the Client, but have not been completely acknowledged.
> - QoS 1 and QoS 2 messages pending transmission to the Client.
> - QoS 2 messages which have been received from the Client, but have not been completely acknowledged.
> - Optionally, QoS 0 messages pending transmission to the Client.

#### 4.7 websocket 支持

支持

#### 4.8 系统主题

**mqttx broker** 内置部分系统主题，用户可酌情使用。

系统主题不支持如下特性：

- 集群：系统主题不支持集群，包括消息及订阅
- 持久化：系统主题消息不支持持久化，包括订阅关系
- QoS: 不支持 QoS 1,2 仅支持 QoS 0

**注意**：***topic 安全机制*** 同样会影响客户端订阅系统主题, 未授权客户端将无法订阅系统主题

系统主题可分两种：

1. 状态主题：反应 **broker** 自身状态的主题
2. 功能主题：对外提供功能性支持的主题

##### 4.8.1 状态主题

客户端可通过订阅系统主题获取 **broker** 状态，目前系统支持如下状态主题：

| 主题                                | 描述                                                         |
| ----------------------------------- | ------------------------------------------------------------ |
| `$SYS/broker/{brokerId}/status`     | 触发方式：订阅此主题的客户端会定期（`mqttx.sys-topic.interval`）收到 broker 的状态，该状态涵盖下面所有主题的状态值. <br/> **注意：客户端连接断开后，订阅取消** |
| `$SYS/broker/activeConnectCount`    | 立即返回当前的活动连接数量<br/>触发：订阅一次触发一次        |
| `$SYS/broker/time`                  | 立即返回当前时间戳<br/>触发：订阅一次触发一次                |
| `$SYS/broker/version`               | 立即返回 `broker` 版本<br/>触发：订阅一次触发一次            |
| `$SYS/broker/receivedMsg`           | 立即返回 `broker` 启动到现在收到的 `MqttMessage`, 不含 `ping`<br/>触发：订阅一次触发一次 |
| `$SYS/broker/sendMsg`               | 立即返回 `broker` 启动到现在发送的 `MqttMessage`, 不含 `pingAck`<br/>触发：订阅一次触发一次 |
| `$SYS/broker/uptime`                | 立即返回 `broker` 运行时长，单位***秒***<br/>触发：订阅一次触发一次 |
| `$SYS/broker/maxActiveConnectCount` | 立即返回 `broker` 运行至今的最大 `tcp` 连接数<br/>触发：订阅一次触发一次 |

系统主题 `$SYS/broker/{brokerId}/status` 中的 **brokerId** 为配置项参数（见 ***[6.1 配置项](#61-配置项)***），可通过携带通配符的主题 `$SYS/broker/+/status` 订阅。

响应对象格式为 `json` 字符串：

```json
{
    "activeConnectCount": 1,
    "maxActiveConnectCount": 2,
    "receivedMsg": 6,
    "sendMsg": 77,
    "timestamp": "2021-03-23T23:05:37.035",
    "uptime": 149,
    "version": "1.0.7.RELEASE"
}
```

| field                   | 说明                            |
| ----------------------- | ------------------------------- |
| `activeConnectCount`    | 当前活跃连接数量                |
| `maxActiveConnectCount` | 最大活跃连接数量                |
| `receiveMsg`            | 收到消息数量，不含 **ping**     |
| `sendMsg`               | 发送消息数量，不含 **pingAck**  |
| `timestamp`             | 时间戳；(`yyyy-MM-dd HH:mm:ss`) |
| `uptime`                | broker 上线时长，单位秒         |
| `version`               | `mqttx` 版本                    |

##### 4.8.2 功能主题

此功能需求源自 issue: [监听MQTT客户端状态（在线、离线） · Issue #8 · Amazingwujun/mqttx (github.com)](https://github.com/Amazingwujun/mqttx/issues/8)

| 主题                                                   | 描述                                                         |
| ------------------------------------------------------ | ------------------------------------------------------------ |
| `$SYS/broker/{borkerId}/clients/{clientId}/connected`    | 客户端上线通知主题 <br/>触发：当某个客户端上线后，**broker** 会发送消息给该主题 |
| `$SYS/broker/{borkerId}/clients/{clientId}/disconnected` | 客户端下线通知主题<br/>触发：当某个客户端掉线后，**broker** 会发送消息给该主题 |

这两个系统主题支持通配符，举例：

1. `$SYS/broker/+/clients/#`: 匹配客户端上下线通知主题
2. `$SYS/broker/+/clients/+/connected`: 匹配客户端上线通知主题
3. `$SYS/broker/+/clients/+/disconnected`: 匹配客户端下线通知主题

#### 4.9 消息桥接支持

支持消息中间件：

- [x] kafka

消息桥接功能可方便的对接消息队列中间。

1. `mqttx.message-bridge.enable`：开启消息桥接功能
2. `mqttx.bridge-topics`：需要桥接消息的主题，主题必须符合 **kafka** 对 **topic** 的要求

`mqttx` 收到客户端 ***发布*** 的消息后，先判断桥接功能是否开启，然后再判断主题是否是需要桥接消息的主题，最后发布消息到 ***MQ***。

**仅支持单向桥接：device(client) => mqttx => MQ**

#### 4.10 主题限流支持

使用基于令牌桶算法的 `com.jun.mqttx.utils.RateLimiter` 对指定主题进行流量限制。

> 令牌桶算法参见：https://stripe.com/blog/rate-limiters
>
> 简单解释一下令牌桶概念：有一个最大容量为 `capacity` 的令牌桶，该桶以一定的速率补充令牌（`replenish-rate`），每次调用接口时消耗一定量（`token-consumed-per-acquire`）的令牌，令牌数目足够则请求通过。

**主题限流仅适用于 `qos` 等于 *0*  的消息**。

配置举例：

```yml
mqttx:
  rate-limiter:
    enable: true
    topic-rate-limits:
      # 例一
      - topic: "/test/a"
        capacity: 9
        replenish-rate: 4
        token-consumed-per-acquire: 3
      # 例二
      - topic: "/test/b"
        capacity: 5
        replenish-rate: 5
        token-consumed-per-acquire: 2
```

- `capacity`: 桶容量
- `replenish-rate`: 令牌填充速率
- `token-consumed-per-acquire`: 每次请求消耗令牌数量

`QPS` 计算公式：

1. 最大并发数：公式为 `QPS = capacity ÷ token-consumed-per-acquire`
    1. 示例一：`9 ÷ 3 = 3`
    2. 示例二：`5 ÷ 2 = 2.5`
2. 最大持续并发数：公式 `QPS = replenish-rate ÷ token-consumed-per-acquire`
    1. 示例一：`4 ÷ 3 ≈ 1.3`
    2. 示例二：`5 ÷ 2 = 2.5`

#### 4.11 消息持久化支持

`mqttx` 的持久化依赖 `redis` , `mqttx` 会持久化 `cleanSession = false & qos > 0` 的消息, 消息被 `Serializer` 序列化为字节数组后存储在 `redis`。

目前 `mqttx` 提供了两种序列化实现：

1. `JsonSerializer`
2. `KryoSerializer`

默认使用 `JsonSerializer`, 这是为了和之前的项目兼容；`v1.0.6.release` 版本后 `KryoSerializer` 将成为默认序列化实现。

可通过配置 `mqttx.serialize-strategy` 修改序列化实现。

#### 4.12 基础认证支持

`mqttx` 提供基础客户端认证服务。

配置项：

1. `mqttx.auth.url`: 提供认证服务的接口地址。
2. `mqttx.auth.timeout`: `HttpClient` 请求超时
3. `mqttx.auth.is-mandatory`: 是否强制要求校验用户名与密码

用户在配置文件中声明 `mqtt.auth.url` 后，对象 `com.jun.mqttx.service.impl.DefaultAuthenticationServiceImpl` 使用 `HttpClient` 发出 `POST` 请求给 `mqttx.auth.url`。 

请求内容为 `mqtt conn` 报文中的 `username, password`.

```curl
POST / HTTP/1.1
Host: mqttx.auth.url
Content-Type: application/json
Content-Length: 91

{
    "clientId": "device_id_test",
    "username": "mqttx",
    "password": "123456"
}
```

认证成功后响应对象为 `json` 格式字符串:

```json
{
    "authorizedSub": [
        "subTopic1",
        "subTopic2"
    ],
    "authorizedPub": [
        "pubTopic1",
        "pubTopic2"
    ]
}
```

认证成功返回响应可配合  [4.5 topic 安全支持](#45-topic-安全支持) 使用。

注意：

- 接口返回 `http status = 200` 即表明**认证成功**, 其它状态值一律为**认证失败**



## 5 开发者说

1. 感谢 **Jetbrains** 为开源项目提供的 License

    <img src="https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.png" alt="Jetbrains" height="150"/>    

2. 长期更新维护的分支
   1. `v1.0`: 基于 `jdk8` 且 redis io 为 **blocking** 模式.
   2. `v1.2`: 基于 `jdk17` 且 redis io 为 **non-blocking** 模式.

3. 为使 ***mqttx*** 项目变得更好，请使用及学习该项目的同学主动反馈使用情况给我（提 issue 或加群反馈）

4. 后续工作
   - [ ] `v1.0.8.RELEASE` 版本开发
   - [ ] `v1.1.0.RELEASE` 版本开发
   - [x] `v1.2` 版本开发
   - [ ] `v2.0` 版本开发
   - [x] bug 修复

5. `v1.2` 版本由 **JDK8** 升级至 **JDK17**

6. `v2.0` 版本分支将作为 **mqttv5** 协议版本开始迭代

7. 考虑使用 *gossip* 协议实现集群功能，集群功能不再依赖 *redis or kafka*

8. 请作者喝杯 **丝绒拿铁** 😊

   <img src="https://z3.ax1x.com/2021/07/15/Wm53vj.jpg" alt="coffee" height="300" />

9. 交流群

    <img src="https://s1.ax1x.com/2020/10/10/0ytoSx.jpg" alt="群二维码" height="300" />

## 6 附表

### 6.1 配置项

`src/main/resources` 目录下有三个配置文件：

1. `application.yml`
2. `application-dev.yml`
3. `application-prod.yml`

后两个配置文件目的是区分不同环境下的配置，便于管理。

配置项说明：

| 配置                                                     | 默认值                          | 说明                                                         |
| -------------------------------------------------------- | ------------------------------- | ------------------------------------------------------------ |
| `mqttx.version`                                          | 取自 `pom.xml`                  | 版本                                                         |
| `mqttx.broker-id`                                        | 取自 `pom.xml`                  | 应用标志, 唯一                                               |
| `mqttx.heartbeat`                                        | `60s`                           | 初始心跳，会被 conn 消息中的 keepalive 重置                  |
| `mqttx.host`                                             | `0.0.0.0`                       | 监听地址                                                     |
| `mqttx.so-backlog`                                       | `512`                           | tcp 连接处理队列                                             |
| `mqttx.enable-topic-sub-pub-secure`                      | `false`                         | 客户订阅/发布主题安全功能，开启后将限制客户端发布/订阅的主题 |
| `mqttx.ignore-client-self-pub`                           | `true`                          | 忽略 client 发送给自己的消息（当 client 发送消息给自己订阅的主题） |
| `mqttx.max-bytes-in-message`                             | `8092`                          | mqttx 允许接收的最大报文载荷，单位 `byte`.                   |
| `mqttx.serialize-strategy`                               | `json`                          | `broker` 采用的序列化策略，**集群策略*必须*一致**。          |
| `mqttx.redis.cluster-session-hash-key`                   | `mqttx.session.key`             | redis map key；用于集群的会话存储                            |
| `mqttx.redis.topic-prefix`                               | `mqttx:topic:`                  | 主题前缀； topic <==> client 映射关系保存                    |
| `mqttx.redis.retain-message-prefix`                      | `mqttx:retain:`                 | 保留消息前缀, 保存 retain 消息                               |
| `mqttx.redis.pub-msg-set-prefix`                         | `mqttx:client:pubmsg:`          | client pub消息 redis set 前缀； 保存 pubmsg，当收到 puback 获取 pubrec 后删除 |
| `mqttx.redis.pub-rel-msg-set-prefix`                     | `mqttx:client:pubrelmsg:`       | client pubRel 消息 redis set 前缀；保存 pubrel 消息 flag，收到 pubcom 消息删除 |
| `mqttx.redis.topic-set-key`                              | `mqttx:alltopic`                | topic 集合，redis set key 值；保存全部主题                   |
| `mqttx.redis.message-id-prefix`                          | `mqttx:messageId:`              | 非 `cleanSession` client 的 `messageId`, 使用 `redis INCR` 指令 |
| `mqttx.redis.client-topic-set-prefix`                    | `mqttx:client:topicset:`        | client 订阅的主题 redis set 前缀; 保存 client 订阅的全部主题 |
| `mqttx.cluster.enable`                                   | `false`                         | 集群开关                                                     |
| `mqttx.cluster.inner-cache-consistancy-key`              | `mqttx:cache_consistence`       | 应用启动后，先查询 redis 中无此 key 值，然后在检查一致性     |
| `mqttx.cluster.type`                                     | `redis`                         | 集群消息中间件类型                                           |
| `mqttx.ssl.enable`                                       | `false`                         | ssl 开关                                                     |
| `mqttx.ssl.client-auth`                                  | `NONE`                          | 客户端证书校验                                               |
| `mqttx.ssl.key-store-location`                           | `classpath: tls/mqttx.keystore` | keyStore 位置                                                |
| `mqttx.ssl.key-store-password`                           | `123456`                        | keyStore 密码                                                |
| `mqttx.ssl.key-store-type`                               | `pkcs12`                        | keyStore 类别                                                |
| `mqttx.socket.enable`                                    | `true`                          | socket 开关                                                  |
| `mqttx.socket.port`                                      | `1883`                          | socket 监听端口                                              |
| `mqttx.websocket.enable`                                 | `false`                         | websocket 开关                                               |
| `mqttx.websocket.port`                                   | `8083`                          | websocket 监听端口                                           |
| `mqttx.websocket.path`                                   | `/mqtt`                         | websocket path                                               |
| `mqttx.share-topic.share-sub-strategy`                   | `round`                         | 负载均衡策略, 目前支持随机、轮询                             |
| `mqttx.sys-topic.enable`                                 | `false`                         | 系统主题功能开关                                             |
| `mqttx.sys-topic.interval`                               | `60s`                           | 定时发布间隔                                                 |
| `mqttx.message-bridge.enable`                            | `false`                         | 消息桥接功能开关                                             |
| `mqttx.message-bridge.topics`                            | `null`                          | 需要桥接消息的主题列表                                       |
| `mqttx.rate-limiter.enable`                              | `false`                         | 主题限流开关                                                 |
| `mqttx.rate-limiter.token-rate-limit`                    |                                 | 参见 [主题限流支持](#410-主题限流支持) 配置举例说明          |
| `mqttx.auth.url`                                         | `null`                          | mqtt conn username/password 认证服务接口地址                 |
| `mqttx.auth.timeout`                                     | `3s`                            | readTimeout                                                  |
| `mqttx.auth.is-mandatory`                                | `false`                         | 是否必须验证 `conn` 报文中的用户名与密码                     |
| `mqttx.sharable-payload.payload-key-prefix`              | `mqttx:sharable-payload:`       | 共享载荷存储 *redis key prefix*                              |
| `mqttx.sharable-payload.unique-id-client-ids-set-prefix` | `mqttx:unique-id:client-ids:`   | 共享载荷关联的客户端 *id* 列表                               |
| `mqttx.sharable-payload.clean-work-interval`             | `1m`                            | 清洗定时间隔。共享载荷清理任务之间的间隔                     |
| `mqttx.sharable-payload.threshould-in-message`           | `128`                           | 共享载荷生效阈值；大于配置项阈值时，载荷共享。               |

