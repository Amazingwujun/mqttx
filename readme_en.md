# MQTTX Project

![license](https://img.shields.io/github/license/tensorflow/tensorflow.svg) ![language](https://img.shields.io/badge/language-java-orange.svg)

[中文](./readme.md) | English

- [1 Introduction](#1-introduction)
    - [1.1 Quick Start](#11-quick-start)
    - [1.2 Project dependencies](#12-project-dependencies)
    - [1.3 Online Examples](#13-online-examples)
- [2 Architecture](#2-architecture)
    - [2.1 Directory Structure](#21-directory-structure)
- [3 Containerized Deployment](#3-containerized-deployment)
- [4 Function description](#4-function-description)
    - [4.1 qos support](#41-qos-support)
    - [4.2 topicFilter support](#42-topicfilter-support)
    - [4.3 Cluster Support](#43-cluster-support)
    - [4.4 ssl support](#44-ssl-support)
    - [4.5 topic Security Support](#45-topic-security-support)
    - [4.6 Shared theme support](#46-shared-theme-support)
    - [4.7 websocket support](#47-websocket-support)
    - [4.8 System Theme](#48-system-theme)
    - [4.9 Message Bridge Support](#49-message-bridge-support)
- [5 The Developer says](#5-the-developer-says)
- [6 Schedule](#6-schedule)
    - [6.1 Configuration Items](#61-configuration-items)
    - [6.2 Release Notes](#62-release-notes)
        - [6.2.1 v1.0](#621-v10)
        - [6.2.2 v1.1](#622-v11)
    - [6.3 Benchmark](#63-benchmark)
        - [6.3.1 CleanSessionTrue](#631-cleansessiontrue)
        - [6.3.2 CleanSessionFalse](#632-cleansessionfalse)

## 1 Introduction

`Mqttx` is developed based on [MQTT v3.1.1](http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html) protocol
and aims to provide  **Mqtt broker** with ***easy to use*** and ***superior performance***.

### 1.1 Quick start

1. Packing

- Test mode: run `mvnw -P test -DskipTests=true clean package`
- Development mode:
    1. Start the `redis` instance
    2. Run `mvnw -P dev -DskipTests=true clean package`

2. Run

- Run the command: `java -jar mqttx-1.0.5.BETA.jar`

*Quick start-test mode* Legend:

<img src="https://s1.ax1x.com/2020/09/27/0kJp3F.gif" alt="Quick start" style="zoom: 80%;" />

- Test mode
    1. The cluster function is forcibly closed
    2. The message is stored in memory instead of `redis`

- Development mode
    1. The message will be persisted to `redis`, the default connection is `localhost:6376` without password

The so-called **test mode** and **development mode** are just for students to quickly start projects and test function
tests. After being familiar with the project, students can modify ***[6.1 Configuration Item](#61-Configuration Item)***
to turn on or off the functions provided by `mqttx`.

> `mqttx` relies on `redis` to achieve message persistence, clustering and other functions. It can also be implemented using other middleware (`mysql`, `mongodb`, `kafka`, etc.), while `springboot` has `spring-boot-starter -***` and other pluggable components, convenient for everyone to modify the default implementation

### 1.2 Project dependencies

- [x] redis: cluster message, message persistence
- [x] kafka: Bridge message support

other instructions:

1. The project uses lombok, please install the corresponding plug-in to use ide

> It is recommended to use [Intellij IDEA](https://www.jetbrains.com/idea/) for development tools :blush:
>
> Example: `idea` needs to install the plug-in `Lombok`, `settings> Build,Execution, Deployment> Compiler> Annotation Processor` to enable `Enable annotation processing`

### 1.3 Online Examples

A singleton service of `mqttx` is deployed in the cloud for functional testing:

1. Does not support ssl
2. Websocket is turned on, it can pass the http://tools.emqx.io/ test, only need to modify the domain name
   to: `119.45.158.51` (port and address remain unchanged)
3. Support shared subscription function
4. Deployment version `v1.0.5.BETA`

![websocket](https://s1.ax1x.com/2020/09/05/wV578J.png)

## 2 Architecture

`mqttx` supports client authentication and topic publish/subscribe authentication functions. If you need to use it
together, the recommended architecture is as follows:

![Architecture Diagram](https://s1.ax1x.com/2020/07/28/ak6KAO.png)

> Customer authentication services are implemented by users themselves

Internal realization framework relationship (only key items are listed):

![ak6mB6.png](https://s1.ax1x.com/2020/07/28/ak6mB6.png)

### 2.1 Directory structure

```
├─java
│  └─com
│      └─jun
│          └─mqttx
│              ├─broker         # mqtt protocol implementation and processing package
│              │  ├─codec       # codec 
│              │  └─handler     # Message processor (pub, sub, connn, etc)
│              ├─config         # configuration, mainly bean declaration
│              ├─constants      # constant
│              ├─consumer       # cluster message consumer
│              ├─entity         # entity class
│              ├─exception      # Exception class
│              ├─service        # Business service (user authentication, message storage, etc.) interface
│              │  └─impl        # default implementation
│              └─utils          # Tools
└─resources                     # Resource file (application.yml is in this folder)
    └─tls                       # ca storage address
```

## 3 Containerized deployment

In order to facilitate the rapid deployment of the project, the introduction of docker

> 1. Before performing local deployment, you need to download docker first.
> 2. The port mapping (`1883, 8083`) is dead written in the docker-compose file. If you modify the port configuration of `mqttx`, you should also modify it in `docker-compose.yml`

1. Package the project as target/*.jar through the packaging function provided by the IDE
2. Enter the directory at the same level of `dockerfile` and execute `docker build -t mqttx:v1.0.4.RELEASE .`
3. Execute `docker-compose up`

## 4 Function description

#### 4.1 qos support

| qos0 | qos1 | qos2 |
| ---- | ---- | ---- |
| Support | Support | Support |

In order to support qos1 and qos2, `redis` is introduced as the persistence layer. This part has been encapsulated as an
interface, which can be replaced by itself (for example, using `mysql`).

#### 4.2 topicFilter support

1. Support multi-level wildcard `#` and single-level wildcard `+`
2. Topics ending with `/` are not supported, such as `a/b/`, please change to `a/b`.
3. For other rules, see***[mqtt v3.1.1](http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html) 4.7 Topic
   Names and Topic Filters***

> **mqttx** only verifies the subscription topicFilter. The publish topic is not checked for validity. You can enable [4.5 topic security support](#45-topic-security support) to limit the topics that the client can publish .

For example:

| topicFilter | match topics |
| ----------- | ----------------------- |
| /a/b/+ | /a/b/abc, /a/b/test |
| a/b/# | a/b, a/b/abc, a/b/c/def |
| a/+/b/# | a/nani/b/abc |
| /+/a/b/+/c | /aaa/a/b/test/c |

The verification tool class is: `com.jun.mqttx.utils.TopicUtils`

#### 4.3 Cluster Support

The project introduced `redis pub/sub` to distribute messages to support the cluster function. If you need to modify it
to `kafka` or other `mq`, you need to modify the configuration class `ClusterConfig` and replace the implementation
class `InternalMessageServiceImpl`.

![ak6nHK.png](https://s1.ax1x.com/2020/07/28/ak6nHK.png)

1. `mqttx.cluster.enable`: function switch, default `false`

> Versions prior to `v1.0.5.RELEASE` have bugs in cluster message processing and cannot be used.

#### 4.4 ssl support

To enable ssl, you should first have *ca* (self-signed or purchased), and then modify several configurations in
the `application.yml` file:

1. `mqttx.ssl.enable`: function switch, default `false`, and control both `websocket` and `socket`
2. `mqttx.ssl.key-store-location`: certificate address, based on `classpath`
3. `mqttx.ssl.key-store-password`: certificate password
4. `mqttx.ssl.key-store-type`: keystore type, such as `PKCS12`
5. `mqttx.ssl.client-auth`: Whether the server needs to verify the client certificate, the default is `false`

> The `mqttx.keystore` in the `resources/tls` directory is for testing only, password: `123456`.
>
> Certificate loading tool class: `com/jun/mqttx/utils/SslUtils.java`

#### 4.5 topic Security Support

In order to restrict client subscriptions to topic, add topic subscription & publishing authentication mechanism:

1. `mqttx.enable-topic-sub-pub-secure`: function switch, default `false`
2. The interface `AuhenticationService` needs to be implemented when using it. The returned object of this interface
   contains `authorizedSub, authorizedPub` to store the list of `topic` that the client is authorized to subscribe and
   publish.
3. The broker verifies the client permissions during message subscription and publishing

Supported theme types:

- [x] Normal theme
- [x] Shared topic
- [x] System theme

#### 4.6 Shared theme support

Shared subscription is the content stipulated by the `mqtt5` protocol, and many mqs (such as `kafka`) have been
implemented.

1. `mqttx.share-topic.enable`: function switch, default `true`
2. Format: `$share/{ShareName}/{filter}`, `$share` is the prefix, `ShareName` is the shared subscription name,
   and `filter` is the non-shared subscription topic filter.
3. Currently supports three rules: `hash`, `random`, and `round`

The following image shows the difference between shared themes and regular themes:

![share-topic](https://s1.ax1x.com/2020/09/22/wXddnU.png)

`msg-a` message distribution strategy depends on the configuration item `mqttx.share-topic.share-sub-strategy`

You can cooperate with the session of `cleanSession = 1`. After the client sharing the topic disconnects, the server
will remove the subscription, so that the message of the shared topic will only be distributed to online clients.

***CleanSession*** Introduction: `mqtt3.1.1` protocol stipulates that when `cleanSession = 1`, all states (excluding
retained messages) associated with the session will be deleted after the connection is disconnected (`mqtt5` added The
session timeout setting, interested students can find out). After `mqttx v1.0.5.BETA` version (included), the session
messages of `cleanSession = 1` are stored in memory, which has extremely high performance.

> If CleanSession is set to 1, the Client and Server **MUST** discard any previous Session and start a new one. This Session lasts as long as the Network Connection. State data associated with this Session **MUST NOT** be reused in any subsequent Session [MQTT-3.1.2-6].
>
> The Session state in the Client consists of:
>
>-QoS 1 and QoS 2 messages which have been sent to the Server, but have not been completely acknowledged.
> -QoS 2 messages which have been received from the Server, but have not been completely acknowledged.
>
> The Session state in the Server consists of:
>
>- The existence of a Session, even if the rest of the Session state is empty.
>- The Client’s subscriptions.
>- QoS 1 and QoS 2 messages which have been sent to the Client, but have not been completely acknowledged.
>- QoS 1 and QoS 2 messages pending transmission to the Client.
>- QoS 2 messages which have been received from the Client, but have not been completely acknowledged.
>- Optionally, QoS 0 messages pending transmission to the Client.

#### 4.7 websocket support

supported

#### 4.8 System Theme

The client can obtain the broker status by subscribing to system topics. Currently, the system supports the following
topics:

| topic | repeat | comment |
| -------------------------------- | ------- | ----------------------------------------------------------- |
| `$SYS/broker/status` | `false` | Clients subscribing to this topic will periodically (`mqttx.sys-topic.interval`) receive the status of the broker, which covers the status values ​​of all topics below. <br/>**
Note: After the client connection is disconnected, the subscription is cancelled** |
| `$SYS/broker/activeConnectCount` | `true` | Immediately return the current number of active connections |
| `$SYS/broker/time` | `true` | Return the current timestamp immediately |
| `$SYS/broker/version` | `true` | Return to `broker` version immediately |

> `repeat`:
>
>- `repeat = false`: Only subscribe once, the broker will publish data to this topic regularly.
>- `repeat = true`: subscribe once, the broker publishes once, and can subscribe multiple times.
>
> Note:
>
> 1. *topic security mechanism* will also affect the client's subscription to system topics, and unauthorized clients will not be able to subscribe to system topics
> 2. System topic subscription will not be persistent

The response object format is `json` string:

```json
{
"activeConnectCount": 2,
"timestamp": "2020-09-18 15:13:46",
"version": "1.0.5.ALPHA"
}
```

| field | Description |
| -------------------- | ------------------------------- |
| `ActiveConnectCount` | Current number of active connections |
| `timestamp` | Timestamp; (`yyyy-MM-dd HH:mm:ss`) |
| `version` | `mqttx` version |

#### 4.9 Message Bridge Support

Support message middleware:

- [x] kafka

The message bridging function can conveniently connect to the middle of the message queue.

1. `mqttx.message-bridge.enable`: enable message bridge function
2. `mqttx.bridge-topics`: Topics that need to bridge messages

After `mqttx` receives the message from the client ***publishing***, it first judges whether the bridging function is
enabled, and then judges whether the subject is the subject that needs to be bridged, and finally publishes the message
to ***MQ***.

**Only supports one-way bridge: device(client) => mqttx => MQ**

## 5 The developer says

1. In the cluster state, consider the function of integrating service registration, which is convenient for managing the
   cluster state. You may use `consul`. Do or not see my later thoughts.

   > Actually I want to introduce `SpringCloud`, but I feel that `springcloud` is a bit heavy, so I might open a branch to implement it.

2. bug fix and optimization, this will continue, but mainly rely on the students who use and learn `mqttx` to feedback
   the problem to me (if there is no feedback, I will treat it as no ~ Tanshou.jpg)

   > This is actually very important, but as of now, few students have come to me for feedback. I am a person with limited power after all.

3. The [mqttx-admin](https://github.com/Amazingwujun/mqttx-admin) management platform based on `vue2.0`, `element-ui` is
   currently being developed. The function update of `mqttx` will be suspended for a while Time~~(Recently
   watching [mqtt5](http://docs.oasis-open.org/mqtt/mqtt/v5.0/csprd02/mqtt-v5.0-csprd02.html))~~. During the project
   development process, it was discovered that some changes to `mqttx` were needed, but these changes should not be
   pushed to the mqttx master (for example, topic security authentication needs to cooperate with `mqttx-platform`, I
   may introduce [Retrofit](https:// square.github.io/retrofit/) to handle interface calls, in fact, you can use `feign`
   , I think these two are similar), I should open a business branch to handle this. By the way, writing projects
   with `javascript` is so cool, why didn't you think?

   > Originally, I needed to devote some energy to the derivative project of `mqttx-admin`, but later I found that `mqttx` still had too many things to do, and I had to change the plan.

4. [benchmark](#63-benchmark) indicates that the performance of mqttx may be improved. I will modify the processing
   logic of `pub/sub` in `v1.1.0.RELEASE`

   > Mainly `StringRedisTemplate` => `ReactiveStringRedisTemplate`, change **synchronous** to **asynchronous**

5. Introduction to development direction

   ~~`v1.0.5.RELEASE` becomes the first **LTS** version of `mqttx`, and `v1.0` will be maintained and updated based on
   it. To improve stand-alone performance, the `v1.1` version will be fully asynchronous.
   Subsequent [mqtt5](http://docs.oasis-open.org/mqtt/mqtt/v5.0/csprd02/mqtt-v5.0-csprd02.html) protocol support may be
   the first to start from `v1.0`. ~~

   `mqttx` creates two branches:

    - v1.0: `com.jun.mqttx.service.impl` synchronization interface
    - v1.1: `com.jun.mqttx.service.impl` changed to asynchronous interface

   [mqtt5](http://docs.oasis-open.org/mqtt/mqtt/v5.0/csprd02/mqtt-v5.0-csprd02.html) Supported from `v1.0`, no surprise
   is ` v1.0.6.RELEASE`.

6. Exchange group

<img src="https://s1.ax1x.com/2020/10/10/0ytoSx.jpg" alt="Group QR code" height="300" />

## 6 Schedule

### 6.1 Configuration items

There are three configuration files in the `src/main/resources` directory:

1. `application.yml`
2. `application-dev.yml`
3. `application-prod.yml`

The purpose of the latter two configuration files is to distinguish configurations in different environments for easy
management.

Configuration item description:

| Configuration | Default | Description |
| ---------------------------------------- | ----------------------------- | ------------------------------------------------------------ |
| `mqttx.version` | From `pom.xml` | Version |
| `mqttx.brokerId` | `1` | Application logo, unique |
| `mqttx.heartbeat` | `60s` | The initial heartbeat will be reset by keepalive in the conn message |
| `mqttx.host` | `0.0.0.0` | Listening address |
| `mqttx.soBacklog` | `512` | tcp connection processing queue |
| `mqttx.enableTopicSubPubSecure` | `false` | Customer subscription/publishing topic security function, when enabled, it will restrict the topics that the client can publish/subscribe to |
| `mqttx.enableInnerCache` | `true` | Every time you publish a message, you need to query redis to get the list of subscribed clients. After enabling this function, a topic-client relationship mapping will be established in the memory, and the application can directly access the data in the memory |
| `mqttx.enableTestMode` | `false` | Test mode switch, the system enters the test mode after it is turned on |
| `mqttx.redis.clusterSessionHashKey` | `mqttx.session.key` | redis map key; session storage for cluster |
| `mqttx.redis.topicPrefix` | `mqttx:topic:` | topic prefix; topic <==> client mapping relationship preservation |
| `mqttx.redis.retainMessagePrefix` | `mqttx:retain:` | Keep message prefix, save retain message |
| `mqttx.redis.pubMsgSetPrefix` | `mqttx:client:pubmsg:` | client pub message redis set prefix; save pubmsg, and delete it after receiving puback to get pubrec |
| `mqttx.redis.pubRelMsgSetPrefix` | `mqttx:client:pubrelmsg:` | client pubRel message redis set prefix; save pubrel message, receive pubcom message delete |
| `mqttx.redis.topicSetKey` | `mqttx:alltopic` | topic collection, redis set key value; save all topics |
| `mqttx.cluster.enable` | `false` | Cluster switch |
| `mqttx.cluster.innerCacheConsistancyKey` | `mqttx:cache_consistence` | After the application is started, first query the redis without this key value, and then check the consistency |
| `mqttx.ssl.enable` | `false` | ssl switch |
| `mqttx.ssl.client-auth` | `false` | Client certificate verification |
| `mqttx.ssl.keyStoreLocation` | `classpath: tls/mqttx.keystore` | keyStore location |
| `mqttx.ssl.keyStorePassword` | `123456` | keyStore password |
| `mqttx.ssl.keyStoreType` | `pkcs12` | keyStore category |
| `mqttx.socket.enable` | `true` | socket switch |
| `mqttx.socket.port` | `1883` | Socket listening port |
| `mqttx.websocket.enable` | `false` | websocket switch |
| `mqttx.websocket.port` | `8083` | websocket listening port |
| `mqttx.websocket.path` | `/mqtt` | websocket path |
| `mqttx.share-topic.enable` | `true` | Shared topic function switch |
| `mqttx.share-topic.share-sub-strategy` | `round` | Load balancing strategy, currently supports random, polling, hashing |
| `mqttx.sys-topic.enable` | `false` | System topic function switch |
| `mqttx.sys-topic.interval` | `60s` | Timed release interval |
| `mqttx.sys-topic.qos` | `0` | Topic qos |
| `mqttx.message-bridge.enable` | `false` | Message bridge function switch |
| `mqttx.message-bridge.topics` | `null` | List of topics that need to bridge messages |

### 6.2 Release Notes

#### 6.2.1 v1.0

- **v1.0.6.RELEASE (under development)**
    - [x] [mqtt5](http://docs.oasis-open.org/mqtt/mqtt/v5.0/csprd02/mqtt-v5.0-csprd02.html) support
    - [x] bug fixes and optimization
- **v1.0.5.RELEASE**
    - [x] Test mode support
    - [x] `epoll` support,
      see [https://netty.io/wiki/native-transports.html](https://netty.io/wiki/native-transports.html)
    - [x] Optimize the message processing mechanism of `cleanSession`
    - [x] Message bridge
    - [x] bug fixes and optimizations
- **v1.0.4.RELEASE**
    - [x] websocket support
    - [x] Cluster status self-check
    - [x] bug fixes and optimization
- **v1.0.3.RELEASE**
    - [x] bug fix
- **v1.0.2.RELEASE**
    - [x] Shared topics are added to the polling strategy
    - [x] bug fixes and optimizations
- **v1.0.1.RELEASE**
    - [x] Cluster function support based on `redis`
    - [x] Shared theme support
    - [x] Theme permission function
    - [x] bug fixes and optimization
- **v1.0.0.RELEASE**
    - [x] `mqttv3.1.1` complete protocol implementation

#### 6.2.2 v1.1

- ***v1.1.0.RELEASE (under development)***

    - [x] `redis` is implemented synchronously to asynchronous to improve performance

### 6.3 Benchmark

The test conditions are simple and the results are for reference only.

Version: ***MQTTX v1.0.5.BETA***

Tools: ***[mqtt-bench](https://github.com/takanorig/mqtt-bench)***

machine:

| System | cpu | Memory |
| ------- | --------- | ----- |
| `win10` | `i5-4460` | `16G` |

#### 6.3.1 CleanSessionTrue

1. Enable `redis`
2. `cleanSession`: ***true***

> **In fact, `pub` message storage does not use redis, for the reason, see the introduction of `cleanSession` in [Shared Topic](#46-Shared Topic Support)**

Execute `java -jar -Xmx1g -Xms1g mqttx-1.0.5.BETA.jar`

- ***qos0***

```
C:\Users\Jun\go\windows_amd64>mqtt-bench.exe -broker=tcp://localhost:1883 -action=pub -clients=1000 -qos=0 -count=1000
2020-09-30 15:33:54.462089 +0800 CST Start benchmark
2020-09-30 15:34:33.6010217 +0800 CST End benchmark

Result: broker=tcp://localhost:1883, clients=1000, totalCount=1000000, duration=39134ms, throughput=25553.23messages/sec
```

- ***qos1***

```
C:\Users\Jun\go\windows_amd64>mqtt-bench.exe -broker=tcp://localhost:1883 -action=pub -clients=1000 -qos=1 -count=1000
2020-09-30 15:29:17.9027515 +0800 CST Start benchmark
2020-09-30 15:30:25.0316915 +0800 CST End benchmark

Result: broker=tcp://localhost:1883, clients=1000, totalCount=1000000, duration=67124ms, throughput=14897.80messages/sec
```

- ***qos2***

```
C:\Users\Jun\go\windows_amd64>mqtt-bench.exe -broker=tcp://localhost:1883 -action=pub -clients=1000 -qos=2 -count=1000
2020-09-30 15:37:00.0678207 +0800 CST Start benchmark
2020-09-30 15:38:55.4419847 +0800 CST End benchmark

Result: broker=tcp://localhost:1883, clients=1000, totalCount=1000000, duration=115369ms, throughput=8667.84messages/sec
```

| Number of concurrent connections | Behavior | Single message size | Single connection message number | Total number of messages | qos | Time-consuming | qps |
| ------------ | -------- | ------------ | -------------- | -------- | ---- | -------- | ------- |
| `1000` | Post message | `1024byte` | `1000` | One million | `0` | `39.1s` | `25553` |
| `1000` | Post message | `1024byte` | `1000` | One million | `1` | `67.1s` | `14897` |
| `1000` | Post message | `1024byte` | `1000` | One million | `2` | `115.3s` | `8667` |

**Resource consumption: `cpu: 25%`, `mem 440 MB`**

#### 6.3.2 CleanSessionFalse

1. Enable `redis`
2. `cleanSession`: ***false***

Execute `java -jar -Xmx1g -Xms1g mqttx-1.0.5.BETA.jar`

- **qos0**

```
C:\Users\Jun\go\windows_amd64>mqtt-bench.exe -broker=tcp://localhost:1883 -action=pub -clients=1000 -qos=0 -count=1000
2020-09-30 17:03:55.7560928 +0800 CST Start benchmark
2020-09-30 17:04:36.2080909 +0800 CST End benchmark

Result: broker=tcp://localhost:1883, clients=1000, totalCount=1000000, duration=40447ms, throughput=24723.71messages/sec
```

- **qos1**

```
C:\Users\Jun\go\windows_amd64>mqtt-bench.exe -broker=tcp://localhost:1883 -action=pub -clients=1000 -qos=1 -count=1000
2020-09-30 17:06:18.9136484 +0800 CST Start benchmark
2020-09-30 17:08:20.9072865 +0800 CST End benchmark

Result: broker=tcp://localhost:1883, clients=1000, totalCount=1000000, duration=121992ms, throughput=8197.26messages/sec
```

- **qos2**

```
C:\Users\Jun\go\windows_amd64>mqtt-bench.exe -broker=tcp://localhost:1883 -action=pub -clients=1000 -qos=2 -count=1000
2020-09-30 17:09:35.1314262 +0800 CST Start benchmark
2020-09-30 17:13:10.7914125 +0800 CST End benchmark

Result: broker=tcp://localhost:1883, clients=1000, totalCount=1000000, duration=215656ms, throughput=4637.01messages/sec
```

| Number of concurrent connections | Behavior | Single message size | Number of single connection messages | Total number of messages | qos | Time-consuming | qps |
| ------------ | -------- | ------------ | -------------- | -------- | ---- | -------- | ------- |
| `1000` | Post message | `1024byte` | `1000` | One million | `0` | `40.4s` | `24723` |
| `1000` | Post message | `1024byte` | `1000` | One million | `1` | `121.9s` | `8197` |
| `1000` | Post message | `1024byte` | `1000` | One million | `2` | `215.6s` | `4637` |

**Resource consumption: `cpu: 45%`, `mem 440 MB`**