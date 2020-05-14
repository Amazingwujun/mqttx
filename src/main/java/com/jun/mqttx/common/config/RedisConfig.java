package com.jun.mqttx.common.config;

import com.jun.mqttx.consumer.InternalMessageSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.*;

import static com.jun.mqttx.common.constant.InternalMessageEnum.*;

/**
 * Redis 配置
 *
 * @author Jun
 * @date 2020-05-14 09:33
 */
@Configuration
public class RedisConfig {

    /**
     * 消息适配器
     *
     * @param subscriber 消息订阅者
     */
    @Bean
    public MessageListener messageListenerAdapter(InternalMessageSubscriber subscriber) {
        MessageListenerAdapter mla = new MessageListenerAdapter();
        mla.setDelegate(subscriber);
        mla.setSerializer(RedisSerializer.string());
        return mla;
    }

    /**
     * 消息监听者容器
     *
     * @param redisConnectionFactory {@link RedisConnectionFactory}
     * @param messageListener        {@link MessageListener}
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory redisConnectionFactory,
                                                                       MessageListener messageListener) {
        RedisMessageListenerContainer redisMessageListenerContainer = new RedisMessageListenerContainer();
        redisMessageListenerContainer.setConnectionFactory(redisConnectionFactory);

        Map<MessageListener, Collection<? extends Topic>> listenerMap = new HashMap<>();
        List<Topic> list = new ArrayList<>(6);
        list.add(new ChannelTopic(PUB.getChannel()));
        list.add(new ChannelTopic(PUB_ACK.getChannel()));
        list.add(new ChannelTopic(PUB_REC.getChannel()));
        list.add(new ChannelTopic(PUB_COM.getChannel()));
        list.add(new ChannelTopic(PUB_REL.getChannel()));
        list.add(new ChannelTopic(DISCONNECT.getChannel()));
        listenerMap.put(messageListener, list);

        redisMessageListenerContainer.setMessageListeners(listenerMap);

        return redisMessageListenerContainer;
    }
}
