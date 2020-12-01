package com.jun.mqttx.config;

import com.jun.mqttx.constants.ShareStrategy;
import io.netty.handler.ssl.ClientAuth;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

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

    /** broker id。区分集群内不同的 broker（如果集群功能开启） */
    private Integer brokerId = 1;

    /** 心跳, 默认 60S;如果 客户端通过 conn 设置了心跳周期，则对应的 channel 心跳为指定周期 */
    private Duration heartbeat = Duration.ofMinutes(1);

    /** ip */
    private String host = "0.0.0.0";

    /** tcp连接队列 */
    private Integer soBacklog = 512;

    /** 主题安全订阅开关，默认关 */
    private Boolean enableTopicSubPubSecure = false;

    /**
     * 内部缓存机制，用于性能提升. 这个参数值必须集群一致，也就是说如果存在多个 mqttx 服务，
     * 那么这些服务的 enableInnerCache 值必须相同，否则会出现预期外的行为。
     */
    private Boolean enableInnerCache = true;

    /**
     * 功能测试模式：
     * 1. 不依赖 redis, 使用内存保存消息
     * 2. 关闭集群
     */
    private Boolean enableTestMode = false;

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

    /**
     * redis 配置
     *
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
    }

    /**
     * 集群配置
     */
    @Data
    public static class Cluster{

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
     *
     * 共享 topic 支持, 实现参考 MQTT v5, 默认关。目前仅支持根据发送端 clientId 进行 hash 后的共享策略，
     * 实现见 {@link com.jun.mqttx.broker.handler.PublishHandler} <code>chooseClient(List,String)</code> 方法.
     */
    @Data
    public static class ShareTopic {

        /** 开关 */
        private Boolean enable = true;

        /**
         * 共享订阅消息分发策略, 默认轮询
         * <ul>
         *     <li>{@link ShareStrategy#random} 随机</li>
         *     <li>{@link ShareStrategy#hash}  哈希</li>
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

        /** 系统主题qos, 默认qos0; 参数适用所有的系统主题. ps: 除开特殊需求，qos0 应该是比较合适的*/
        private Integer qos = 0;
    }

    /**
     * 消息桥接配置
     *
     */
    @Data
    public static class MessageBridge{

        /** 开关 */
        private Boolean enable = false;

        /** 需要桥接消息的主题, 不允许通配符 */
        private Set<String> topics = null;
    }
}