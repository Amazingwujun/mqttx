`mqttx` 基于 [mqtt v3.1.1](http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html) 官方协议文档开发。
项目运行的方式：

  1. 使用`springboot`推荐的启动方式 `java -jar app.jar`，使用 `mvn clean package` 打包，这种方式需要修改配置文件(`resources/application.yml`)中 redis 地址和端口。

     > mqttx default redis 连接使用的配置：`localhost:6379` **无密码**

  2. 基于 `docker` 容器化部署，这个就比较简单，具体的步骤见 **容器化部署**

中间件依赖：

1. **redis**

其他依赖：
1. 项目使用了 lombok（用于消除get() set()） ，使用 ide 请安装对应的插件
> 举例：idea 需要安装插件 Lombok, settings > Build,Execution,Deployment > Compiler > Annotation Processor 开启 Enable annotation processing

## 架构

由于 `mqttx` 额外添加了客户端认证、topic 发布/订阅鉴权功能，如果需要配套使用，建议的架构如下图：

![架构图](https://s1.ax1x.com/2020/07/28/ak6KAO.png)

> 客户认证服务由使用者自行实现

内部实现框架关系(仅列出关键项)：

![ak6mB6.png](https://s1.ax1x.com/2020/07/28/ak6mB6.png)

目录结构：

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
## 容器化部署

为了方便项目快速的部署，引进 docker

> 执行本地部署动作前，需要先下载docker

1. 通过IDE提供的打包功能将项目打包为 target/*.jar
2. 进入 dockerfile 同级目录，执行 `docker build -t mqttx:v0.1 .`
3. 执行 docker-compose up

## 功能说明

#### 1、 qos 支持

| qos0 | qos1 | qos2 |
| ---- | ---- | ---- |
| 支持 | 支持 | 支持 |

为支持 qos1、qos2，引入 `redis` 作为持久层，这部分已经封装成接口，可自行替换实现（比如采用 `mysql`）。

#### 2、topicFilter 支持

1. 支持多级通配符 `#`与单级通配符 `+`，不支持通配符 `$`
2. 不支持以 `/` 开头的topic，比如 /a/b 会被判定为非法 topic，请改为 a/b。
3. 不支持以 `/`结尾的topic，比如 a/b/，请改为 a/b。
4. 其它规则见 [mqtt v3.1.1](http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html) 4.7 Topic Names and Topic Filters

> ps：实际上 **mqttx** 仅对订阅 topicFilter 进行校验，publish 的 topic 是没有做合法性检查的。

举例：

| topicFilter | match topics       |
| ----------- | ------------------ |
| a/b/+       | a/b/abc            |
| a/b/#       | a/b/abc, a/b/c/def |
| a/+/b/#     | a/nani/b/abc       |
| +/a/b/+/c   | aaa/a/b/test/c     |

校验工具类为：`com.jun.mqttx.utils.TopicUtis`

#### 3、集群支持

项目引入 `redis pub/sub ` 分发消息以支持集群功能。如果需要修改为 `kafka` 或其它 mq ，需要修改配置类 `ClusterConfig` 及替换实现类 `InternalMessageServiceImpl`。

![ak6nHK.png](https://s1.ax1x.com/2020/07/28/ak6nHK.png)

1. `biz.cluster.enable`：功能开关，默认 `false`

#### 4、ssl支持

开启 ssl 你首先应该有了ca，然后修改 `application.yml` 文件中几个配置：

1. `biz.ssl.enable`：功能开关，默认 `false`，同时控制 `websocket` 与 `socket`
2. `biz.ssl.key-store-location`: 证书地址，基于 `classpath`
3. `biz.ssl.key-store-password`: 证书密码
4. `biz.ssl.key-store-type`: keystore 类别，如 `PKCS12`

#### 5、topic 安全机制

为了对 client 订阅 topic 进行限制，项目引入了简单的 topic 订阅&发布鉴权机制:

1. `biz.enable-topic-sub-pub-secure`: 功能开关，默认 `false`
2. 使用时需要同步实现接口 `AuhenticationService` ，该接口返回对象中含有 `authorizedSub,authorizedPub` 存储 client 被授权订阅及发布的 `topic` 列表。
3. broker 在消息订阅及发布都会校验客户端权限

#### 6、共享订阅机制

共享订阅是 `mqtt5` 协议规定的内容，很多 MQ 都有实现。`mqttx` 的实现也是基于 `mqtt5`。
1. `biz.share-topic.enable`: 功能开关，默认 `true` 
2. 格式: `$share/{ShareName}/{filter}`, `$share` 为前缀, `ShareName` 为共享订阅名, `filter` 就是非共享订阅主题过滤器。
3. 目前支持 `hash`, `random`, `round` 三种规则


#### 7、websocket 支持

### 路线图

基于我个人的认知，`mqttx` 接下来可能的开发计划：

1. ~~考虑整合 `SpringCloud`~~ 
2. bug fix and optimization
3. 目前正在开发基于 `vue2.0`, `element-ui` 的 [mqttx-admin](https://github.com/Amazingwujun/mqttx-admin) 管理平台，`mqttx` 的功能更新可能会暂停一段时间(最近在看 [mqtt5](http://docs.oasis-open.org/mqtt/mqtt/v5.0/csprd02/mqtt-v5.0-csprd02.html))

任何问题，请联系我。邮箱：85998282@qq.com.

---

# 演示项目 lineyou - 服务端基于`mqttx`

[lineyou](https://github.com/Amazingwujun/lineyou) 基于 `javafx`开发，运用 `netty、spring、fxlauncher、jfoniex、fontawesomefx、protobuf` 等技术实现的 im 程序，具备基本的聊天交互功能。

![登录、注册](https://upload-images.jianshu.io/upload_images/23452769-9dd8ad215b34f36c.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

![添加好友](https://upload-images.jianshu.io/upload_images/23452769-4bdf69435041fe0e.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

![聊天界面](https://upload-images.jianshu.io/upload_images/23452769-9121c23d1867ffb8.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)


可通过地址 https://wws.lanzous.com/ifPKretv6za 下载后解压运行
