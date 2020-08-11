package com.jun.mqttx.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 业务配置
 *
 * @author Jun
 * @date 2020-03-03 20:57
 */
@Data
@Component
@ConfigurationProperties(prefix = "biz")
public class BizConfig {
    //@formatter:off

    /** 心跳 */
    private Duration heartbeat;

    /** 端口 */
    private Integer port;

    /** ip */
    private String host;

    /** ssl认证开关 */
    private Boolean sslEnable;

    /** tcp连接队列 */
    private Integer soBacklog;

    /** keyStore 位置 */
    private String keyStoreLocation;

    /** keyStore 密码 */
    private String keyStorePassword;

    /** redis map key,应用于集群的会话存储 */
    private String clusterSessionHashKey;

    /** redis increase 生成messageId的前缀 */
    private String messageIdPrefix;

    /** 主题前缀 */
    private String topicPrefix;

    /** 保留消息前缀 */
    private String retainMessagePrefix;

    /** client pub消息 redis set 前缀 */
    private String pubMsgSetPrefix;

    /** client pubRel消息 redis set 前缀 */
    private String pubRelMsgSetPrefix;

    /**
     * topic集合，redis set key值
     */
    private String topicSetKey;

    /**
     * broker id ,用于区分集群内不同的 broker
     */
    private Integer brokerId;

    /**
     * 集群功能开关 ,默认 false
     */
    private Boolean enableCluster = false;

    /**
     * 主题安全订阅开关，默认关
     */
    private Boolean enableTopicSubPubSecure = false;

    /**
     * 内部缓存机制，用于性能提升; 如果用户没有设置，非集群状态下，默认开启缓存;如果用户没有设置，集群状态下，默认关闭缓存
     * 这个参数值必须集群一致，也就是说如果存在多个 mqttx 服务，那么这些服务的 enableInnerCache 值必须相同，否则会出现预期外的行为。
     */
    private Boolean enableInnerCache;

    /**
     * 共享 topic 支持, 实现参考 MQTT v5, 默认关。目前仅支持根据发送端 clientId 进行 hash 后的共享策略，
     * 实现见 {@link com.jun.mqttx.broker.handler.PublishHandler} <code>chooseClient(List,String)</code> 方法.
     */
    private Boolean enableShareTopic = false;
    //@formatter:on
}
