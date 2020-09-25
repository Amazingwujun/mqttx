package com.jun.mqttx.config;

import com.jun.mqttx.consumer.InternalMessageSubscriber;
import com.jun.mqttx.consumer.Watcher;
import com.jun.mqttx.service.IInternalMessagePublishService;
import com.jun.mqttx.service.impl.InternalMessagePublishServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.*;
import java.util.concurrent.Executors;

import static com.jun.mqttx.constants.InternalMessageEnum.*;

/**
 * 集群配置
 *
 * @author Jun
 * @since 1.0.4
 */
@Configuration
@ConditionalOnExpression("${mqttx.cluster.enable:true} and ${mqttx.enableTestMode:false}")
public class ClusterConfig {

    @SuppressWarnings("rawtypes")
    @Bean
    public InternalMessageSubscriber internalMessageSubscriber(List<Watcher> watchers, MqttxConfig mqttxConfig) {
        return new InternalMessageSubscriber(watchers, mqttxConfig);
    }

    @Bean
    public IInternalMessagePublishService internalMessagePublishService(StringRedisTemplate stringRedisTemplate) {
        return new InternalMessagePublishServiceImpl(stringRedisTemplate);
    }

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
        // 替换默认实现（默认实现未使用线程池）, 使用单线程执行任务
        redisMessageListenerContainer.setTaskExecutor(Executors.newSingleThreadExecutor());

        Map<MessageListener, Collection<? extends Topic>> listenerMap = new HashMap<>();
        List<Topic> list = new ArrayList<>(8);
        list.add(new ChannelTopic(PUB.getChannel()));
        list.add(new ChannelTopic(PUB_ACK.getChannel()));
        list.add(new ChannelTopic(PUB_REC.getChannel()));
        list.add(new ChannelTopic(PUB_COM.getChannel()));
        list.add(new ChannelTopic(PUB_REL.getChannel()));
        list.add(new ChannelTopic(DISCONNECT.getChannel()));
        list.add(new ChannelTopic(ALTER_USER_AUTHORIZED_TOPICS.getChannel()));
        list.add(new ChannelTopic(SUB_UNSUB.getChannel()));
        listenerMap.put(messageListener, list);

        redisMessageListenerContainer.setMessageListeners(listenerMap);

        return redisMessageListenerContainer;
    }
}