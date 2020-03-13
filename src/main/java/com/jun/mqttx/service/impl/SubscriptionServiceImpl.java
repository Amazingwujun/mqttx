package com.jun.mqttx.service.impl;

import com.alibaba.fastjson.JSON;
import com.jun.mqttx.common.config.BizConfig;
import com.jun.mqttx.entity.ClientSub;
import com.jun.mqttx.service.ISubscriptionService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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

    public SubscriptionServiceImpl(StringRedisTemplate stringRedisTemplate, BizConfig bizConfig) {
        Assert.notNull(stringRedisTemplate, "stringRedisTemplate can't be null");

        this.stringRedisTemplate = stringRedisTemplate;
        this.topicPrefix = bizConfig.getTopicPrefix();

        Assert.hasText(this.topicPrefix, "topicPrefix can't be null");
    }

    /**
     * 目前topic仅支持全字符匹配
     *
     * @param clientId      客户id
     * @param clientSubList 订阅主题列表
     */
    @Override
    public void subscribe(String clientId, List<ClientSub> clientSubList) {
        clientSubList.forEach(clientSub ->
                stringRedisTemplate.opsForSet()
                        .add(topicPrefix + clientSub.getTopic(), JSON.toJSONString(clientSub))
        );
    }

    /**
     * 解除订阅
     *
     * @param clientId 客户id
     * @param topics   主题列表
     */
    @Override
    public void unsubscribe(String clientId, List<String> topics) {
        topics.forEach(topic -> stringRedisTemplate.opsForSet().remove(topicPrefix + topic, clientId));
    }


    /**
     * 返回订阅主题的客户列表
     *
     * @param topic 主题
     * @return 客户ID列表
     */
    @Override
    public List<ClientSub> searchSubscribeClientList(String topic) {
        Set<String> members = stringRedisTemplate.opsForSet().members(topicPrefix + topic);
        if (CollectionUtils.isEmpty(members)) {
            return Collections.EMPTY_LIST;
        }

        List<ClientSub> clientSubList = new ArrayList<>(members.size());
        members.forEach(member -> clientSubList.add(JSON.parseObject(member, ClientSub.class)));
        return clientSubList;
    }

    @Override
    public void clearClientSubscriptions(String clientId) {
        //Warning keys 这个操作在redis里面存在性能问题
        Set<String> keys = stringRedisTemplate.keys(topicPrefix + "*");
        if (keys == null) {
            return;
        }
        unsubscribe(clientId, new ArrayList<>(keys));
    }

    @Override
    public void removeTopic(String topic) {
        stringRedisTemplate.delete(topicPrefix + topic);
    }
}
