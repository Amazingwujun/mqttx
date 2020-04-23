package com.jun.mqttx.service.impl;

import com.jun.mqttx.common.config.BizConfig;
import com.jun.mqttx.entity.ClientSub;
import com.jun.mqttx.service.ISubscriptionService;
import com.jun.mqttx.utils.TopicUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * 主题订阅服务
 *
 * @author Jun
 * @date 2020-03-09 21:06
 */
@Service
public class SubscriptionServiceImpl implements ISubscriptionService {

    private StringRedisTemplate stringRedisTemplate;

    /**
     * 订阅主题前缀
     */
    private String topicPrefix;

    /**
     * 主题集合
     */
    private String topicSetKey;

    public SubscriptionServiceImpl(StringRedisTemplate stringRedisTemplate, BizConfig bizConfig) {
        Assert.notNull(stringRedisTemplate, "stringRedisTemplate can't be null");

        this.stringRedisTemplate = stringRedisTemplate;
        this.topicPrefix = bizConfig.getTopicPrefix();
        this.topicSetKey = bizConfig.getTopicSetKey();

        Assert.hasText(this.topicPrefix, "topicPrefix can't be null");
        Assert.hasText(this.topicSetKey, "topicSetKey can't be null");
    }

    /**
     * 目前topic仅支持全字符匹配
     *
     * @param clientSub 客户订阅信息
     */
    @Override
    public void subscribe(ClientSub clientSub) {
        String topic = clientSub.getTopic();
        String clientId = clientSub.getClientId();
        int qos = clientSub.getQos();

        //保存topic <---> client 映射
        stringRedisTemplate.opsForHash()
                .put(topicPrefix + topic, clientId, String.valueOf(qos));

        //将topic保存到redis set集合中
        stringRedisTemplate.opsForSet().add(topicSetKey, topic);
    }

    /**
     * 解除订阅
     *
     * @param clientId 客户id
     * @param topics   主题列表
     */
    @Override
    public void unsubscribe(String clientId, List<String> topics) {
        topics.forEach(topic -> stringRedisTemplate.opsForHash().delete(topicPrefix + topic, clientId));
    }


    /**
     * 返回订阅主题的客户列表
     *
     * @param topic 主题
     * @return 客户ID列表
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<ClientSub> searchSubscribeClientList(String topic) {
        Set<String> allTopic = stringRedisTemplate.opsForSet().members(topicSetKey);
        if (CollectionUtils.isEmpty(allTopic)) {
            return Collections.EMPTY_LIST;
        }

        List<ClientSub> clientSubList = new ArrayList<>();
        allTopic.stream()
                .filter(e -> TopicUtils.match(topic, e))
                .forEach(e -> {
                    Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(topicPrefix + e);
                    if (!CollectionUtils.isEmpty(entries)) {
                        entries.forEach((k, v) -> {
                            String key = (String) k;
                            String val = (String) v;
                            clientSubList.add(new ClientSub(key, Integer.parseInt(val), e));
                        });
                    }
                });

        return clientSubList;
    }

    @Override
    public void clearClientSubscriptions(String clientId) {
        Set<String> keys = stringRedisTemplate.opsForSet().members(topicSetKey);
        if (CollectionUtils.isEmpty(keys)) {
            return;
        }
        unsubscribe(clientId, new ArrayList<>(keys));
    }

    /**
     * 移除 topic
     *
     * @param topic 主题
     */
    @Override
    public void removeTopic(String topic) {
        stringRedisTemplate.opsForSet().remove(topicSetKey, topic);
        stringRedisTemplate.delete(topicPrefix + topic);
    }
}
