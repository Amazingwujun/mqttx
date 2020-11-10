package com.jun.mqttx.constants;

/**
 * 集群消息枚举
 *
 * @author Jun
 * @since 1.0.4
 */
public enum InternalMessageEnum {

    PUB(1, ClusterTopic.PUB),

    PUB_ACK(2, ClusterTopic.PUB_ACK),

    PUB_REC(3, ClusterTopic.PUB_REC),

    PUB_COM(4, ClusterTopic.PUB_COM),

    PUB_REL(5, ClusterTopic.PUB_REL),

    DISCONNECT(6, ClusterTopic.DISCONNECT),

    ALTER_USER_AUTHORIZED_TOPICS(7, ClusterTopic.ALTER_USER_AUTHORIZED_TOPICS),

    SUB_UNSUB(8, ClusterTopic.SUB_UNSUB);

    private final int type;

    /**
     * redis pub/sub channel
     */
    private final String channel;

    InternalMessageEnum(int type, String channel) {
        this.type = type;
        this.channel = channel;
    }

    public String getChannel() {
        return channel;
    }

    public int getType() {
        return type;
    }
}