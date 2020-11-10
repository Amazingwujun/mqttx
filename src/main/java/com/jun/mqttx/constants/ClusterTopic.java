package com.jun.mqttx.constants;

/**
 * 集群主题
 */
public interface ClusterTopic {

    String PUB = "MQTTX_INTERNAL_PUB";

    String PUB_ACK = "MQTTX_INTERNAL_PUBACK";

    String PUB_REC = "MQTTX_INTERNAL_PUBREC";

    String PUB_COM = "MQTTX_INTERNAL_PUBCOM";

    String PUB_REL = "MQTTX_INTERNAL_PUBREL";

    String DISCONNECT = "MQTTX_INTERNAL_DISCONNECT";

    String ALTER_USER_AUTHORIZED_TOPICS = "MQTTX_ALTER_USER_AUTHORIZED_TOPICS";

    String SUB_UNSUB = "MQTTX_INTERNAL_SUB_OR_UNSUB";
}
