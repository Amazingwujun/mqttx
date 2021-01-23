/*
 * Copyright 2002-2020 the original author or authors.
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

package com.jun.mqttx.consumer;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.constants.ClusterTopic;
import com.jun.mqttx.utils.Serializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;


/**
 * 集群消息订阅分发处理器, kafka 实现
 *
 * @since v1.0.6
 */
@Slf4j
public class KafkaInternalMessageSubscriber extends AbstractInnerChannel implements ConsumerSeekAware {

    private volatile boolean onceFlag = false;

    public KafkaInternalMessageSubscriber(List<Watcher> watchers, Serializer serializer, MqttxConfig mqttxConfig) {
        super(watchers, serializer ,mqttxConfig);
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
        dispatch(value, topic);
    }

    /**
     * 当 partition assignment 变化时，重置消费者的 offset 为 latest, 此举为避免集群消费者重新上线后消费之前的消息导致数据不一致的问题。
     * 仅在项目初始化时执行一次此函数.
     * <p>
     * ps: redis pub/sub 客户端重连后只会收到最新的消息，而 kafka 会保存 consumer 消费的 offset, 该方法可在逻辑上使其行为一致。
     */
    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        if (!onceFlag) {
            onceFlag = true;
            callback.seekToEnd(assignments.keySet());
        }
    }
}
