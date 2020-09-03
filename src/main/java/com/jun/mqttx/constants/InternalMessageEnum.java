package com.jun.mqttx.constants;

/**
 * 集群消息枚举
 *
 * @author Jun
 * @date 2020-05-14 09:36
 */
public enum InternalMessageEnum {

    PUB(1, "INTERNAL_PUB"),

    PUB_ACK(2, "INTERNAL_PUBACK"),

    PUB_REC(3, "INTERNAL_PUBREC"),

    PUB_COM(4, "INTERNAL_PUBCOM"),

    PUB_REL(5, "INTERNAL_PUBREL"),

    DISCONNECT(6, "INTERNAL_DISCONNECT"),

    ALTER_USER_AUTHORIZED_TOPICS(7, "ALTER_USER_AUTHORIZED_TOPICS"),

    SUB_UNSUB(8, "INTERNAL_SUB_OR_UNSUB");

    private int type;

    /**
     * redis pub/sub channel
     */
    private String channel;

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