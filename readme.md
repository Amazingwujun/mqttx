# MQTTX Project

![license](https://img.shields.io/github/license/tensorflow/tensorflow.svg) ![language](https://img.shields.io/badge/language-java-orange.svg)

中文 | [English](./readme_en.md)

- [1 介绍](#1-介绍)
    - [1.1 快速开始](#11-快速开始)
    - [1.2 项目依赖](#12-项目依赖)
    - [1.3 线上实例](#13-线上实例)
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
- [5 开发者说](#5-开发者说)
- [6 附表](#6-附表)
    - [6.1 配置项](#61-配置项)
    - [6.2 版本说明](#62-版本说明)
        - [6.2.1 v1.0](#621-v10)
        - [6.2.2 v1.1](#622-v11)
        - [6.2.3 v2.0](#623-v20)
    - [6.3 Benchmark](#63-benchmark)
        - [6.3.1 CleanSessionTrue](#631-cleansessiontrue)
        - [6.3.2 CleanSessionFalse](#632-cleansessionfalse)
    - [6.4 代码质量分析](#64-代码质量分析)

## 1 介绍

`Mqttx` 基于 [MQTT v3.1.1](http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html) 协议开发，旨在提供 ***易于使用*** 且 ***性能优越*** 的 **mqtt broker**。

### 1.1 快速开始

> 想通过 docker 快速体验？见 [docker 启动](#3-docker-启动)

1. 打包
   1. 启动 `redis` 实例
   2. 运行 `mvnw -P dev -DskipTests=true clean package`
2. 运行
    1. 运行命令：`java -jar mqttx-1.0.7.RELEASE.jar`

*快速开始-测试模式* 图例：

<img src="https://s1.ax1x.com/2020/09/27/0kJp3F.gif" alt="快速开始" style="zoom: 80%;" />

- 测试模式
    1. 集群功能被强制关闭
    2. 消息保存在内存而不是 `redis`

- 开发模式
    1. 消息会持久化到 `redis`, 默认连接 `localhost:6376` 无密码

所谓**测试模式**、**开发模式**只是方便同学们快速启动项目，方便测试功能测试。熟悉项目后，同学们可通过修改 ***[6.1 配置项](#61-配置项)*** 开启或关闭 `mqttx` 提供的各项功能。

> `mqttx` 默认依赖 `redis` 实现消息持久化、集群等功能，使用其它中间件（`mysql`, `mongodb`, `kafka` 等）同样能够实现，而 `springboot` 具备 `spring-boot-starter-***`  等各种可插拔组件，方便大家修改默认的实现

### 1.2 项目依赖

- [x] **Redis**： 集群消息、消息持久化
- [x] **Kafka**：桥接消息支持，集群消息（可选功能）

其它说明：

1. 项目使用了 **lombok**，使用 **ide** 请安装对应的插件

> 开发工具建议使用 [Intellij IDEA](https://www.jetbrains.com/idea/) :blush:
>
> 举例：`idea` 需要安装插件 `Lombok`, `settings > Build,Execution,Deployment > Compiler > Annotation Processor` 开启 `Enable annotation processing`

### 1.3 线上实例

云端部署了一个 `mqttx` 单例服务，可供功能测试：

1. 不支持 `ssl`
2. 开启了 `websocket`, 可通过 http://tools.emqx.io/ 测试，仅需将域名修改为：`119.45.158.51`(端口、地址不变)
3. 支持共享订阅功能
4. 部署版本 `v1.0.6.RELEASE`

![websocket](https://s1.ax1x.com/2020/09/05/wV578J.png)

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

> 镜像已上传至  docker-hub , 点击访问：[fantasywujun/mqttx - Docker Hub](https://hub.docker.com/r/fantasywujun/mqttx)

docker 环境安装好后，执行`docker-compose -f ./docker-compose.yml up` 启动, 效果见下图：

![y3R3tI.md.png](https://s3.ax1x.com/2021/02/04/y3R3tI.md.png)

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

- [x] `Kafka`：可选配置
- [x] `Redis`：默认配置

实现原理如下图：

![ak6nHK.png](https://s1.ax1x.com/2020/07/28/ak6nHK.png)

1. `mqttx.cluster.enable`：功能开关，默认 `false`
2. `mqttx.cluster.type`: 消息中间件类型，默认 `redis`

注意事项：

1. `v1.0.5.RELEASE` 之前的版本集群功能存在 bug，无法使用。

2. 如需使用 `kafka` 实现集群消息，需要手动修改配置 `application-*.yml`, 可参考 `application-dev.yml` 中的配置示例 ***3. kafka 集群***。
3. 测试模式开启后，集群功能 **强制** 关闭

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
2. 使用时需要实现接口 `AuhenticationService` ，该接口返回对象中含有 `authorizedSub,authorizedPub` 存储 client 被授权订阅及发布的 `topic` 列表。
3. broker 在消息订阅及发布都会校验客户端权限

支持的主题类型：

- [x] 普通主题
- [x] 共享主题
- [x] 系统主题

#### 4.6 共享主题支持

共享订阅是 `mqtt5` 协议规定的内容，很多 mq(例如 `kafka`) 都有实现。

1. `mqttx.share-topic.enable`: 功能开关，默认 `true`

2. 格式: `$share/{ShareName}/{filter}`, `$share` 为前缀, `ShareName` 为共享订阅名, `filter` 就是非共享订阅主题过滤器。

3. 目前支持 `hash`, `random`, `round` 三种规则

   > `hash` 选出的 **client** 会随着**订阅客户端数量**及**发送消息客户端 `clientId`** 变化而变化

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

## 5 开发者说

1. `v1.0` 版本分支将作为支持 **mqttv3.1.1** 协议版本持续迭代
2. 为使 ***mqttx*** 项目变得更好，请使用及学习该项目的同学主动反馈使用情况给我（提 issue 或加群反馈）
3. 后续工作
   - [x] `v1.0.7.RELEASE` 版本 ***Benchmark***
   - [x] `v1.0.8.RELEASE` 版本开发
   - [x] `v2.0.0.RELEASE` 版本开发
   - [x] bug 修复
4. `v2.0` 版本分支将作为 **mqttv5** 协议版本开始迭代
5. 这段时间工作任务繁重，功能迭代暂时停止，当然 **bug** 我还是会优先处理🙂
6. 交流群

<img src="https://s1.ax1x.com/2020/10/10/0ytoSx.jpg" alt="群二维码" height="300" />

## 6 附表

### 6.1 配置项

`src/main/resources` 目录下有三个配置文件：

1. `application.yml`
2. `application-dev.yml`
3. `application-prod.yml`

后两个配置文件目的是区分不同环境下的配置，便于管理。

配置项说明：

| 配置                                     | 默认值                        | 说明                                                         |
| ---------------------------------------- | ----------------------------- | ------------------------------------------------------------ |
| `mqttx.version`                          | 取自 `pom.xml`               | 版本                                                         |
| `mqttx.broker-id`                       | 取自 `pom.xml`                | 应用标志, 唯一                                               |
| `mqttx.heartbeat`                        | `60s`                         | 初始心跳，会被 conn 消息中的 keepalive 重置                  |
| `mqttx.host`                             | `0.0.0.0`                       | 监听地址                                                     |
| `mqttx.so-backlog`                      | `512`                           | tcp 连接处理队列                                             |
| `mqttx.enable-topic-sub-pub-secure`  | `false`                         | 客户订阅/发布主题安全功能，开启后将限制客户端发布/订阅的主题 |
| `mqttx.enable-inner-cache` | `true`                          | 发布消息每次都需要查询 redis 来获取订阅的客户端列表。开启此功能后，将在内存中建立一个主题-客户端关系映射, 应用直接访问内存中的数据即可 |
| `mqttx.enable-test-mode` | `false` | 测试模式开关，开启后系统进入测试模式; <br/>**注意：测试模式会禁用集群功能** |
| `mqttx.ignore-client-self-pub` | `true` | 忽略 client 发送给自己的消息（当 client 发送消息给自己订阅的主题） |
| `mqttx.serialize-strategy` | `json` | `broker` 采用的序列化策略，**集群策略*必须*一致**。 |
| `mqttx.redis.cluster-session-hash-key` | `mqttx.session.key`             | redis map key；用于集群的会话存储                          |
| `mqttx.redis.topic-prefix`              | `mqttx:topic:`                  | 主题前缀； topic <==> client 映射关系保存               |
| `mqttx.redis.retain-message-prefix`    | `mqttx:retain:`                 | 保留消息前缀, 保存 retain 消息                            |
| `mqttx.redis.pub-msg-set-prefix`      | `mqttx:client:pubmsg:`          | client pub消息 redis set 前缀； 保存 pubmsg，当收到 puback 获取 pubrec 后删除 |
| `mqttx.redis.pub-rel-msg-set-prefix` | `mqttx:client:pubrelmsg:`       | client pubRel 消息 redis set 前缀；保存 pubrel 消息 flag，收到 pubcom 消息删除 |
| `mqttx.redis.topic-set-key`            | `mqttx:alltopic`                | topic 集合，redis set key 值；保存全部主题               |
| `mqttx.redis.message-id-prefix` | `mqttx:messageId:` | 非 `cleanSession` client 的 `messageId`, 使用 `redis INCR` 指令 |
| `mqttx.redis.client-topic-set-prefix` | `mqttx:client:topicset:` | client 订阅的主题 redis set 前缀; 保存 client 订阅的全部主题 |
| `mqttx.cluster.enable`                   | `false`                         | 集群开关                                                     |
| `mqttx.cluster.inner-cache-consistancy-key` | `mqttx:cache_consistence`       | 应用启动后，先查询 redis 中无此 key 值，然后在检查一致性     |
| `mqttx.cluster.type` | `redis` | 集群消息中间件类型 |
| `mqttx.ssl.enable`                       | `false`                         | ssl 开关                                                     |
| `mqttx.ssl.client-auth` | `NONE` | 客户端证书校验 |
| `mqttx.ssl.key-store-location`         | `classpath: tls/mqttx.keystore` | keyStore 位置                                                |
| `mqttx.ssl.key-store-password`         | `123456`             | keyStore 密码                                                |
| `mqttx.ssl.key-store-type`             | `pkcs12`                        | keyStore 类别                                                |
| `mqttx.socket.enable`                    | `true`                          | socket 开关                                                  |
| `mqttx.socket.port`                      | `1883`                          | socket 监听端口                                              |
| `mqttx.websocket.enable`                 | `false`                         | websocket 开关                                               |
| `mqttx.websocket.port`                   | `8083`                          | websocket 监听端口                                           |
| `mqttx.websocket.path`                   | `/mqtt`                         | websocket path                                            |
| `mqttx.share-topic.enable`               | `true`                          | 共享主题功能开关                                             |
| `mqttx.share-topic.share-sub-strategy`   | `round`                         | 负载均衡策略, 目前支持随机、轮询、哈希                       |
| `mqttx.sys-topic.enable` | `false` | 系统主题功能开关 |
| `mqttx.sys-topic.interval` | `60s` | 定时发布间隔 |
| `mqttx.message-bridge.enable` | `false` | 消息桥接功能开关 |
| `mqttx.message-bridge.topics` | `null` | 需要桥接消息的主题列表 |
| `mqttx.rate-limiter.enable` | `false` | 主题限流开关 |
| `mqttx.rate-limiter.token-rate-limit` |  | 参见 [主题限流支持](#410-主题限流支持) 配置举例说明 |

### 6.2 版本说明

**prometheus** 分支为 ***MQTTX*** 整合监控系统 **[Prometheus](https://prometheus.io/)** 的代码，有需要的用户可参考该分支代码.

#### 6.2.1 v1.0

- **v1.0.8.RELEASE（开发中）**
    - [ ] 消息集中持久化到 `redis hmap` 数据结构中，`PubMsg` 仅保存 `hmap` 中的 `payloadId`, 该优化目的在于防止消息膨胀导致的 redis 内存耗用过大。（之前版本消息都是持久化到客户端各自的 `PubMsg`）
- **v1.0.7.RELEASE**
    - [x] 增加序列化框架 ***Kryo*** 的支持
    - [x]  系统主题新增客户端上下线通知主题
    - [x] 修复新增订阅触发 `retain` 消息后，消息分发给全部订阅者的 bug
    - [x] 修复遗嘱消息 `isWillRetain:true` 持久化的bug
    - [x] bug 修复及优化
- **v1.0.6.RELEASE**
    - [x] `netty 4.1.52.Final` 这个版本的 MqttEncoder.java 处理 UnsubAck 响应消息会导致 NPE，直接影响功能，不得不提前结束此版本的开发
    - [x] bug 修复
- **v1.0.5.RELEASE**
    - [x] 测试模式支持
    - [x] `epoll` 支持，见 [https://netty.io/wiki/native-transports.html](https://netty.io/wiki/native-transports.html)
    - [x] 优化 `cleanSession` 消息处理机制
    - [x] 消息桥接
    - [x] bug 修复及优化
- **v1.0.4.RELEASE**
    - [x] websocket 支持
    - [x] 集群状态自检
    - [x] bug 修复及优化
- **v1.0.3.RELEASE**
    - [x] bug 修复
- **v1.0.2.RELEASE**
    - [x] 共享主题加入轮询策略
    - [x] bug 修复及优化
- **v1.0.1.RELEASE**
    - [x] 基于 `redis` 的集群功能支持
    - [x] 共享主题支持
    - [x] 主题权限功能
    - [x] bug 修复及优化
- **v1.0.0.RELEASE**
    - [x] `mqttv3.1.1` 完整协议实现

#### 6.2.2 v1.1

- **v1.1.0.RELEASE（停止开发）**
  - [ ] `redis` 同步转异步实现，提升性能

#### 6.2.3 v2.0

- **v2.0.0.RELEASE（开发中）**
  - [x] [mqtt5](http://docs.oasis-open.org/mqtt/mqtt/v5.0/csprd02/mqtt-v5.0-csprd02.html) 支持

### 6.3 Benchmark

测试条件简陋，结果仅供参考。

版本： ***MQTTX v1.0.5.BETA***

工具： ***[mqtt-bench](https://github.com/takanorig/mqtt-bench)***

机器：

| 系统    | cpu       | 内存  |
| ------- | --------- | ----- |
| `win10` | `i5-4460` | `16G` |

#### 6.3.1 CleanSessionTrue

1. 启用 `redis`
2. `cleanSession` : ***true***

> **实际上 `pub` 消息存储并未走 redis， 原因见 [共享主题](#46-共享主题支持) 中关于 `cleanSession` 的介绍**

执行 `java -jar -Xmx1g -Xms1g mqttx-1.0.5.BETA.jar`

- ***qos0***

```
C:\Users\Jun\go\windows_amd64>mqtt-bench.exe -broker=tcp://localhost:1883 -action=pub -clients=1000 -qos=0 -count=1000
2020-09-30 15:33:54.462089 +0800 CST Start benchmark
2020-09-30 15:34:33.6010217 +0800 CST End benchmark

Result : broker=tcp://localhost:1883, clients=1000, totalCount=1000000, duration=39134ms, throughput=25553.23messages/sec
```

- ***qos1***

```
C:\Users\Jun\go\windows_amd64>mqtt-bench.exe -broker=tcp://localhost:1883 -action=pub -clients=1000 -qos=1 -count=1000
2020-09-30 15:29:17.9027515 +0800 CST Start benchmark
2020-09-30 15:30:25.0316915 +0800 CST End benchmark

Result : broker=tcp://localhost:1883, clients=1000, totalCount=1000000, duration=67124ms, throughput=14897.80messages/sec
```

- ***qos2***

```
C:\Users\Jun\go\windows_amd64>mqtt-bench.exe -broker=tcp://localhost:1883 -action=pub -clients=1000 -qos=2 -count=1000
2020-09-30 15:37:00.0678207 +0800 CST Start benchmark
2020-09-30 15:38:55.4419847 +0800 CST End benchmark

Result : broker=tcp://localhost:1883, clients=1000, totalCount=1000000, duration=115369ms, throughput=8667.84messages/sec
```

| 并发连接数量 | 行为     | 单个消息大小 | 单连接消息数量 | 报文总数 | qos  | 耗时     | qps     |
| ------------ | -------- | ------------ | -------------- | -------- | ---- | -------- | ------- |
| `1000`       | 发布消息 | `1024byte`   | `1000`         | 一百万   | `0`  | `39.1s`  | `25553` |
| `1000`       | 发布消息 | `1024byte`   | `1000`         | 一百万   | `1`  | `67.1s`  | `14897` |
| `1000`       | 发布消息 | `1024byte`   | `1000`         | 一百万   | `2`  | `115.3s` | `8667`  |

**资源消耗：`cpu: 25%`, `mem 440 MB`**

#### 6.3.2 CleanSessionFalse

1. 启用 `redis`
2. `cleanSession`: ***false***

执行 `java -jar -Xmx1g -Xms1g mqttx-1.0.5.BETA.jar`

- **qos0**

```
C:\Users\Jun\go\windows_amd64>mqtt-bench.exe -broker=tcp://localhost:1883 -action=pub -clients=1000 -qos=0 -count=1000
2020-09-30 17:03:55.7560928 +0800 CST Start benchmark
2020-09-30 17:04:36.2080909 +0800 CST End benchmark

Result : broker=tcp://localhost:1883, clients=1000, totalCount=1000000, duration=40447ms, throughput=24723.71messages/sec
```

- **qos1**

```
C:\Users\Jun\go\windows_amd64>mqtt-bench.exe -broker=tcp://localhost:1883 -action=pub -clients=1000 -qos=1 -count=1000
2020-09-30 17:06:18.9136484 +0800 CST Start benchmark
2020-09-30 17:08:20.9072865 +0800 CST End benchmark

Result : broker=tcp://localhost:1883, clients=1000, totalCount=1000000, duration=121992ms, throughput=8197.26messages/sec
```

- **qos2**

```
C:\Users\Jun\go\windows_amd64>mqtt-bench.exe -broker=tcp://localhost:1883 -action=pub -clients=1000 -qos=2 -count=1000
2020-09-30 17:09:35.1314262 +0800 CST Start benchmark
2020-09-30 17:13:10.7914125 +0800 CST End benchmark

Result : broker=tcp://localhost:1883, clients=1000, totalCount=1000000, duration=215656ms, throughput=4637.01messages/sec
```

| 并发连接数量 | 行为     | 单个消息大小 | 单连接消息数量 | 报文总数 | qos  | 耗时     | qps     |
| ------------ | -------- | ------------ | -------------- | -------- | ---- | -------- | ------- |
| `1000`       | 发布消息 | `1024byte`   | `1000`         | 一百万   | `0`  | `40.4s`  | `24723` |
| `1000`       | 发布消息 | `1024byte`   | `1000`         | 一百万   | `1`  | `121.9s` | `8197`  |
| `1000`       | 发布消息 | `1024byte`   | `1000`         | 一百万   | `2`  | `215.6s` | `4637`  |

**资源消耗：`cpu: 45%`, `mem 440 MB`**

### 6.4 代码质量分析

结果取自 [mqttx:  (gitee.com)](https://gitee.com/amazingJun/mqttx) **sonarQube**

[![sonar](https://s3.ax1x.com/2020/12/02/D57mlR.png)](https://imgchr.com/i/D57mlR)

- 漏洞是我将 `keyStore` 密码硬编码写到了配置代码，方便用户测试 `TLS` ，用户可自行替换。

