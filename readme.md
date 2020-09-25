# MQTTX Project 

 ![license](https://img.shields.io/github/license/tensorflow/tensorflow.svg) ![language](https://img.shields.io/badge/language-java-orange.svg)

- [1 介绍](#1-介绍)
    - [1.1 快速开始](#11-快速开始)
    - [1.2 线上实例](#12-线上实例)
- [2 架构](#2-架构)
    - [2.1 目录结构](#21-目录结构)
- [3 容器化部署](#3-容器化部署)
- [4 功能说明](#4-功能说明)
    - [4.1 qos 支持](#41-qos-支持)
    - [4.2 topicFilter 支持](#42-topicfilter-支持)
    - [4.3 集群支持](#43-集群支持)
    - [4.4 ssl 支持](#44-ssl-支持)
    - [4.5 topic 安全支持](#45-topic-安全支持)
    - [4.6 共享主题支持](#46-共享主题支持)
    - [4.7 websocket 支持](#47-websocket-支持)
    - [4.8 系统主题](#48-系统主题)
- [5 路线图](#5-路线图)
- [6 附表](#6-附表)
    - [6.1 配置项](#61-配置项)

## 1 介绍

`Mqttx` 基于 [mqtt v3.1.1](http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html) 协议开发，旨在提供*易于使用*且*兼顾性能*的 **mqtt 服务端**。

### 1.1 快速开始

1. 打包
   - 测试模式：运行  `mvnw -P test -DskipTests=true clean package`

   - 开发模式：
     1. 启动 `redis` 实例
     2. 运行 `mvnw -P dev -DskipTests=true clean package`
2. 运行
   
   1. 运行命令：`java -jar mqttx-1.0.5.BETA.jar`

***快速开始-测试模式***图例：

<img src="https://s1.ax1x.com/2020/09/27/0kJp3F.gif" alt="快速开始" style="zoom: 80%;" />

- 测试模式
  1. 集群功能被强制关闭
  2. 消息保存在内存而不是 `redis`

- 开发模式
    1. 消息会持久化到 `redis`, 默认连接 `localhost:6376` 无密码

所谓的**测试模式**、**开发模式**只是方便同学们快速启动项目，方便测试功能测试。熟悉项目后，同学们可通过修改***[6.1 配置项](#61-配置项)*** 开启或关闭 `mqttx` 提供的各项功能。

> `mqttx` 依赖 `redis` 实现消息持久化、集群等功能，使用其它中间件（`mysql`, `mongodb`, `kafka` 等）同样能够实现，而 `springboot` 具备 `spring-boot-starter-***`  等各种可插拔组件，方便大家修改默认的实现

项目依赖中间件（非测试模式）：

- [x] redis

其它说明：
1. 项目使用了 lombok，使用 ide 请安装对应的插件
> 开发工具建议使用 [Intellij IDEA](https://www.jetbrains.com/idea/) :blush:
> 
> 举例：`idea` 需要安装插件 `Lombok`, `settings > Build,Execution,Deployment > Compiler > Annotation Processor` 开启 `Enable annotation processing`

### 1.2 线上实例

云端部署了一个 `mqttx` 单例服务，可供功能测试：
1. 不支持 ssl
2. 开启了 websocket, 可通过 http://tools.emqx.io/ 测试，仅需将域名修改为：`119.45.158.51`(端口、地址不变)
3. 支持 共享订阅功能
4. 部署版本 `v1.0.4.RELEASE`

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
│              ├─broker         mqtt 协议实现及处理包
│              │  ├─codec       编解码
│              │  └─handler     消息处理器（pub, sub, connn, etc）
│              ├─config         配置，主要是 bean 声明
│              ├─constants      常量
│              ├─consumer       集群消息消费者
│              ├─entity         实体类
│              ├─exception      异常类
│              ├─service        业务服务（用户认证, 消息存储等）接口
│              │  └─impl        默认实现
│              └─utils          工具类
└─resources                     资源文件（application.yml 在此文件夹）
    └─tls                       ca 存放地址
```
## 3 容器化部署

为了方便项目快速的部署，引进 docker

> 1. 执行本地部署动作前，需要先下载 docker。
> 2. docker-compose 文件中写死了端口映射（`1883, 8083`）， 如果你修改了 `mqttx` 的端口配置，则 `docker-compose.yml` 中也应修改

1. 通过IDE提供的打包功能将项目打包为 target/*.jar
2. 进入 `dockerfile` 同级目录，执行 `docker build -t mqttx:v1.0.4.RELEASE .`
3. 执行 `docker-compose up`

## 4 功能说明

#### 4.1 qos 支持

- [x] qos0
- [x] qos1
- [x] qos2

为支持 qos1、qos2，引入 `redis` 作为持久层，这部分已经封装成接口，可自行替换实现（比如采用 `mysql`）。

#### 4.2 topicFilter 支持

1. 支持多级通配符 `#`与单级通配符 `+`，不支持通配符 `$`
3. 不支持以 `/`结尾的topic，比如 a/b/，请改为 a/b。
4. 其它规则见 [mqtt v3.1.1](http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html) 4.7 Topic Names and Topic Filters

>  **mqttx** 仅对订阅 topicFilter 进行校验，publish 的 topic 是没有做合法性检查的。如果 [4.5 topic 安全支持](#45-topic) 功能开启
>
> ，客户端只允许发布消息到被授权的主题。

举例：

| topicFilter | match topics            |
| ----------- | ----------------------- |
| /a/b/+      | /a/b/abc, /a/b/test     |
| a/b/#       | a/b, a/b/abc, a/b/c/def |
| a/+/b/#     | a/nani/b/abc            |
| /+/a/b/+/c  | /aaa/a/b/test/c         |

校验工具类为：`com.jun.mqttx.utils.TopicUtils`

#### 4.3 集群支持

项目引入 `redis pub/sub ` 分发消息以支持集群功能。如果需要修改为 `kafka` 或其它 `mq` ，需要修改配置类 `ClusterConfig` 及替换实现类 `InternalMessageServiceImpl`。

![ak6nHK.png](https://s1.ax1x.com/2020/07/28/ak6nHK.png)

1. `mqttx.cluster.enable`：功能开关，默认 `false`

#### 4.4 ssl 支持

开启 ssl 你首先应该有了 *ca*(自签名或购买)，然后修改 `application.yml` 文件中几个配置：

1. `mqttx.ssl.enable`：功能开关，默认 `false`，同时控制 `websocket` 与 `socket`
2. `mqttx.ssl.key-store-location`: 证书地址，基于 `classpath`
3. `mqttx.ssl.key-store-password`: 证书密码
4. `mqttx.ssl.key-store-type`: keystore 类别，如 `PKCS12`

> `resources/tls` 目录中的 `mqttx.keystore` 仅供测试使用, 密码: `123456`

#### 4.5 topic 安全支持

为了对 client 订阅 topic 进行限制，加入 topic 订阅&发布鉴权机制:

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

下图展示了共享主题与常规主题之间的差异:

![share-topic](https://s1.ax1x.com/2020/09/22/wXddnU.png)

> `msg-a` 消息分发策略取决于配置项 `mqttx.share-topic.share-sub-strategy`
>
> 可以配合 `cleanSession = 1` 的会话，共享主题的客户端断开连接后会被服务端移除订阅，这样共享主题的消息只会分发给在线的客户端。
> 注意: `mqtt3.1.1` 协议规定当 `cleanSession = 1` 时，连接断开后与会话相关联的所有状态（不含 retained 消息）都会被删除（`mqtt5`  增加了会话超时设置，感兴趣的同学可以了解一下）。
> `mqttx v1.0.5.BETA` 版本后(含)，`cleanSession = 1` 的会话消息保存在内存中，具备极高的性能.
>
> > If CleanSession is set to 1, the Client and Server **MUST** discard any previous Session and start a new one. This Session lasts as long as the Network Connection. State data associated with this Session **MUST NOT** be reused in any subsequent Session [MQTT-3.1.2-6].
> >
> > The Session state in the Client consists of:
> >
> > - QoS 1 and QoS 2 messages which have been sent to the Server, but have not been completely acknowledged.
> > - QoS 2 messages which have been received from the Server, but have not been completely acknowledged. 
> >
> > The Session state in the Server consists of:
> >
> > - The existence of a Session, even if the rest of the Session state is empty.
> > - The Client’s subscriptions.
> > - QoS 1 and QoS 2 messages which have been sent to the Client, but have not been completely acknowledged.
> > - QoS 1 and QoS 2 messages pending transmission to the Client.
> > - QoS 2 messages which have been received from the Client, but have not been completely acknowledged.
> > - Optionally, QoS 0 messages pending transmission to the Client. 


#### 4.7 websocket 支持

支持

#### 4.8 系统主题

客户端可通过订阅系统主题获取 broker 状态，目前系统支持如下主题：

| topic                            | repeat  | comment                                                      |
| -------------------------------- | ------- | ------------------------------------------------------------ |
| `$SYS/broker/status`             | `false` | 订阅此主题的客户端会定期（`mqttx.sys-topic.interval`）收到 broker 的状态，该状态涵盖下面所有主题的状态值. <br/>**注意：客户端连接断开后，订阅取消** |
| `$SYS/broker/activeConnectCount` | `true`  | 立即返回当前的活动连接数量                                   |
| `$SYS/broker/time`               | `true`  | 立即返回当前时间戳                                           |
| `$SYS/broker/version`            | `true`  | 立即返回 `broker` 版本                                       |

> `repeat`:
>
> - `repeat = false` : 只需订阅一次，broker 会定时发布数据到此主题. 
> - `repeat = true` : 订阅一次，broker 发布一次，可多次订阅.
>
> 注意：
>
> 1. *topic 安全机制* 同样会影响客户端订阅系统主题, 未授权客户端将无法订阅系统主题
> 2. 系统主题订阅不会持久化

响应对象格式为 `json` 字符串：

```json
{
	"activeConnectCount": 2,
	"timestamp": "2020-09-18 15:13:46",
	"version": "1.0.5.ALPHA"
}
```

| field                | 说明                            |
| -------------------- | ------------------------------- |
| `activeConnectCount` | 当前活跃连接数量                |
| `timestamp`          | 时间戳；(`yyyy-MM-dd HH:mm:ss`) |
| `version`            | `mqttx` 版本                    |



## 5 路线图

基于我个人的认知，`mqttx` 接下来可能的开发计划：

1. 集群态考虑整合服务注册的功能，便于管理集群状态，可能会使用 `consul`，做不做看我后面的想法吧
3. bug fix and optimization，这个会一直继续的，不过主要靠使用和学习 `mqttx` 的同学反馈问题给我（没反馈我就当没有呗~摊手.jpg）
4. 目前正在开发基于 `vue2.0`, `element-ui` 的 [mqttx-admin](https://github.com/Amazingwujun/mqttx-admin) 管理平台，`mqttx` 的功能更新会暂停一段时间~~(最近在看 [mqtt5](http://docs.oasis-open.org/mqtt/mqtt/v5.0/csprd02/mqtt-v5.0-csprd02.html))~~。
项目开发过程中发现需要对 `mqttx` 做一些改动，但这些改动不应该 push 给 mqttx master（比如 topic 安全认证这个功能需要配合 `mqttx-platform`，我可能会引入 [Retrofit](https://square.github.io/retrofit/) 处理接口调用，其实可以用 `feign`，我觉的这两个都差不多），我应该会开一个业务 branch 处理这个事情。话说 `javascript` 写项目可太爽了，以前怎么不觉得?
5. `mqttx` 还没压测过，算了，看心情吧~ （有同学帮忙不？） 
5. `netty 4.1.52.Final 支持了 mqtt5`，em...

任何问题，请联系我。邮箱：85998282@qq.com.

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
| `mqttx.brokerId`                         | `1`                             | 应用标志, 唯一                                               |
| `mqttx.heartbeat`                        | `50s`                           | 初始心跳，会被 conn 消息中的 keepalive 重置                  |
| `mqttx.host`                             | `0.0.0.0`                       | 监听地址                                                     |
| `mqttx.soBacklog`                        | `512`                           | tcp 连接处理队列                                             |
| `mqttx.enableTopicSubPubSecure`          | `false`                         | 客户订阅/发布主题安全功能，开启后将限制客户端发布/订阅的主题 |
| `mqttx.enableInnerCache`                 | `true`                          | 发布消息每次都需要查询 redis 来获取订阅的客户端列表。开启此功能后，将在内存中建立一个主题-客户端关系映射, 应用直接访问内存中的数据即可 |
| `mqttx.enableTestMode` | `false` | 开启测试模式 |
| `mqttx.redis.clusterSessionHashKey`      | `mqttx.session.key`             | redis map key；用于集群的会话存储                          |
| `mqttx.redis.topicPrefix`                | `mqttx:topic:`                  | 主题前缀； topic <--> client 映射关系保存                    |
| `mqttx.redis.retainMessagePrefix`        | `mqttx:retain:`                 | 保留消息前缀, 保存 retian 消息                               |
| `mqttx.redis.pubMsgSetPrefix`            | `mqttx:client:pubmsg:`          | client pub消息 redis set 前缀； 保存 pubmsg，当收到 puback 获取 pubrec 后删除 |
| `mqttx.redis.pubRelMsgSetPrefix`         | `mqttx:client:pubrelmsg:`       | client pubRel 消息 redis set 前缀；保存 pubrel 消息，收到 pubcom 消息删除 |
| `mqttx.redis.topicSetKey`                | `mqttx:alltopic`                | topic 集合，redis set key 值；保存所有的主题                 |
| `mqttx.cluster.enable`                   | `false`                         | 集群开关                                                     |
| `mqttx.cluster.innerCacheConsistancyKey` | `mqttx:cache_consistence`       | 应用启动后，先查询 redis 中无此 key 值，然后在检查一致性     |
| `mqttx.ssl.enable`                       | `false`                         | ssl 开关                                                     |
| `mqttx.ssl.keyStoreLocation`             | `classpath: tls/mqttx.keystore` | keyStore 位置                                                |
| `mqttx.ssl.keyStorePassword`             | `用户手动配置`                  | keyStore 密码                                                |
| `mqttx.ssl.keyStoreType`                 | `pkcs12`                        | keyStore 类别                                                |
| `mqttx.socket.enable`                    | `true`                          | socket 开关                                                  |
| `mqttx.socket.port`                      | `1883`                          | socket 监听端口                                              |
| `mqttx.websocket.enable`                 | `false`                         | websocket 开关                                               |
| `mqttx.websocket.port`                   | `8083`                          | websocket 监听端口                                           |
| `mqttx.websocket.path`                   | `/mqtt`                         | websocket path                                            |
| `mqttx.share-topic.enable`               | `true`                          | 共享主题功能开关                                             |
| `mqttx.share-topic.share-sub-strategy`   | `round`                         | 负载均衡策略, 目前支持随机、轮询、哈希                       |
| `mqttx.sys-topic.enable` | `false` | 系统主题功能开关 |
| `mqttx.sys-topic.interval` | `60s` | 定时发布间隔 |
| `mqttx.sys-topic.qos` | `0` | 主题 qos |
| `mqttx.enableTestMode` | false | 测试模式开关，开启后系统进入测试模式 |



