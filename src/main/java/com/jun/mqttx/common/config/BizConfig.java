package com.jun.mqttx.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

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

    //@formatter:on
}
