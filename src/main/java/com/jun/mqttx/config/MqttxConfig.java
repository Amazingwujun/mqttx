/*
 * Copyright 2020-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jun.mqttx.config;

import com.jun.mqttx.constants.SerializeStrategy;
import com.jun.mqttx.constants.ShareStrategy;
import com.jun.mqttx.entity.TopicRateLimit;
import io.netty.handler.codec.mqtt.MqttConstant;
import io.netty.handler.ssl.ClientAuth;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * 业务配置.
 * <p>
 * 所有项目都有默认配置, 其它类注入配置后不再检查配置是否为空
 *
 * @author Jun
 * @since 1.0.4
 */
@Data
@Component
@ConfigurationProperties(prefix = "mqttx")
public class MqttxConfig {
    //@formatter:off

    /*--------------------------------------------
    |                 基础配置项                   |
    ============================================*/

    /** mqttx 版本 */
    private String version = "unknown";

    /**
     * broker id, 必须唯一, 默认 uuid.
     * <p>
     * 用于区分集群内不同的 broker（如果集群功能开启）
     */
    private String brokerId = UUID.randomUUID().toString().replaceAll("-",  "");

    /** 心跳, 默认 60S;如果 客户端通过 conn 设置了心跳周期，则对应的 channel 心跳为指定周期 */
    private Duration heartbeat = Duration.ofMinutes(1);

    /** ip */
    private String host = "0.0.0.0";

    /** tcp连接队列 */
    private Integer soBacklog = 512;

    /** 主题安全订阅开关，默认关 */
    private Boolean enableTopicSubPubSecure = false;

    /**
     * 序列化策略选择:
     * <ol>
     *     <li>{@link SerializeStrategy#JSON}: 默认项(兼容早期版本)</li>
     *     <li>{@link SerializeStrategy#KRYO}: 优选项，性能领先 json</li>
     * </ol>
     */
    private String serializeStrategy = SerializeStrategy.JSON;

    /**
     * 有时候 client 会发表消息给自己（client 订阅了自己发布的主题），默认情形 mqttx 将过滤该消息.
     */
    private Boolean ignoreClientSelfPub = true;

    /** {@link java.net.http.HttpClient} connectTimeout */
    private Duration httpClientConnectTimeout = Duration.ofSeconds(10);

    /** mqttx 可接受的最大报文大小 */
    private int maxBytesInMessage = MqttConstant.DEFAULT_MAX_BYTES_IN_MESSAGE;

    /*--------------------------------------------
    |                 模块配置项                   |
    ============================================*/

    private Redis redis = new Redis();

    private Cluster cluster = new Cluster();

    private Ssl ssl = new Ssl();

    private Socket socket = new Socket();

    private WebSocket webSocket = new WebSocket();

    private ShareTopic shareTopic = new ShareTopic();

    private SysTopic sysTopic = new SysTopic();

    private MessageBridge messageBridge = new MessageBridge();

    private RateLimiter rateLimiter = new RateLimiter();

    private Auth auth = new Auth();

    /**
     * redis 配置
     * <p>
     * 目前 mqttx 的储存、集群功能的默认实现都依赖 redis，耦合过重不利于其他实现（如 mysql/kafka），先抽出配置项.
     * ps: 实际上集群功能的实现也是基于 redis
     */
    @Data
    public static class Redis {

        /** redis map key,应用于集群的会话存储 */
        private String clusterSessionHashKey = "mqttx:session:key";

        /** 主题前缀 */
        private String topicPrefix = "mqttx:topic:";

        /** 保留消息前缀 */
        private String retainMessagePrefix = "mqttx:retain:";

        /** client pub消息 redis set 前缀 */
        private String pubMsgSetPrefix = "mqttx:client:pubmsg:";

        /** client pubRel 消息 redis set 前缀 */
        private String pubRelMsgSetPrefix = "mqttx:client:pubrelmsg:";

        /** topic集合，redis set key值 */
        private String topicSetKey = "mqttx:alltopic";

        /** 非 cleanSession messageId 获取前缀 */
        private String messageIdPrefix = "mqttx:messageId:";

        /** client topic集合，redis set prefix值 */
        private String clientTopicSetPrefix = "mqttx:client:topicset:";

        /** pubMsg 消息集合，用于存储消息 */
        private String msgPayLoadKey = "mqttx:msg:payload";

        /** 每个payload 关联的订阅用户 */
        private String msgPayLoadClientsSetKey = "mqttx:msg:payload:clients:";
    }

    /**
     * 集群配置
     */
    @Data
    public static class Cluster {

        /** 用于集群内部缓存开启状态一致性检查 */
        private String innerCacheConsistencyKey = "mqttx:cache_consistence";

        /** 集群开关 */
        private Boolean enable = false;

        /** 处理集群消息的中间件类型 */
        private String type = ClusterConfig.REDIS;
    }

    /**
     * ssl 配置
     */
    @Data
    public static class Ssl {

        /** ssl 开关 */
        private Boolean enable = false;

        /** keyStore 位置 */
        private String keyStoreLocation = "classpath:tls/mqttx.keystore";

        /** keyStore 密码 */
        private String keyStorePassword = "123456";

        /** keyStore 类别 */
        private String keyStoreType = "pkcs12";

        /** 客户端认证 */
        private ClientAuth clientAuth = ClientAuth.NONE;
    }

    /**
     * socket 配置
     */
    @Data
    public static class Socket {

        /** 开关 */
        private Boolean enable = true;

        /** 监听端口 */
        private Integer port = 1883;
    }

    /**
     * websocket 配置
     */
    @Data
    public static class WebSocket {

        /** 开关 */
        private Boolean enable = false;

        /** 监听端口 */
        private Integer port = 8083;

        /** uri */
        private String path = "/mqtt";
    }

    /**
     * 共享 topic 配置
     * <p>
     * 共享 topic 支持, 实现参考 MQTT v5, 默认关。目前仅支持根据发送端 clientId 进行 hash 后的共享策略，
     * 实现见 {@link com.jun.mqttx.broker.handler.PublishHandler} <code>chooseClient(List,String)</code> 方法.
     */
    @Data
    public static class ShareTopic {

        /**
         * 共享订阅消息分发策略, 默认轮询
         * <ul>
         *     <li>{@link ShareStrategy#random} 随机</li>
         *     <li>{@link ShareStrategy#round} 轮询</li>
         * </ul>
         * @see ShareStrategy
         */
        private ShareStrategy shareSubStrategy = ShareStrategy.round;
    }

    /**
     * 系统管理 topic
     *
     * <ol>
     *     <li>当应用重启时，会丢失订阅信息，如有需要则应该重新发起系统管理主题的订阅</li>
     *     <li>当 {@link #enableTopicSubPubSecure} 开启时，系统管理主题也会被保护</li>
     * </ol>
     * topic 写死在 {@link com.jun.mqttx.utils.TopicUtils}
     */
    @Data
    public static class SysTopic {

        /** 开关 */
        private Boolean enable = false;

        /** 定时发送时间，默认一分钟 */
        private Duration interval = Duration.ofMinutes(1);
    }

    /**
     * 消息桥接配置
     *
     */
    @Data
    public static class MessageBridge {

        /** 开关 */
        private Boolean enable = false;

        /** 需要桥接消息的主题, 不允许通配符 */
        private Set<String> topics = null;
    }

    /**
     * 主题限流配置
     */
    @Data
    public static class RateLimiter {

        /** 开关 */
        private Boolean enable = false;

        /** 限流主题配置 */
        private Set<TopicRateLimit> topicRateLimits;
    }

    @Data
    public static class Auth {

        /** 是否强制要求密码认证 */
        private Boolean isMandatory = false;

        /** 认证服务接口地址 */
        private String url;

        /** 类似 readTimeout, 见 {@link java.net.http.HttpRequest#timeout()} */
        private Duration timeout = Duration.ofSeconds(3);
    }
}
