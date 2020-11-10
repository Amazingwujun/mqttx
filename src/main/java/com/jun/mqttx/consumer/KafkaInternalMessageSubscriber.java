package com.jun.mqttx.consumer;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.constants.ClusterTopic;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;

import java.nio.charset.StandardCharsets;
import java.util.List;


/**
 * 集群消息订阅分发处理器, kafka 实现
 *
 * @since v1.0.6
 */
@Slf4j
public class KafkaInternalMessageSubscriber extends AbstractInnerChannel {

    public KafkaInternalMessageSubscriber(List<Watcher> watchers, MqttxConfig mqttxConfig) {
        super(watchers, mqttxConfig);
    }

    /**
     * 集群消息处理
     *
     * @param record {@link ConsumerRecord}
     */
    @KafkaListener(topics = {
            ClusterTopic.PUB,
            ClusterTopic.PUB_ACK,
            ClusterTopic.PUB_REC,
            ClusterTopic.PUB_REL,
            ClusterTopic.PUB_COM,
            ClusterTopic.DISCONNECT,
            ClusterTopic.ALTER_USER_AUTHORIZED_TOPICS,
            ClusterTopic.SUB_UNSUB
    })
    public void handlerMessage(ConsumerRecord<String, byte[]> record) {
        byte[] value = record.value();
        String topic = record.topic();
        dispatch(new String(value, StandardCharsets.UTF_8), topic);
    }
}
