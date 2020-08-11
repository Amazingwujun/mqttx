package com.jun.mqttx.service.impl;

import com.jun.mqttx.common.config.BizConfig;
import com.jun.mqttx.common.constant.InternalMessageEnum;
import com.jun.mqttx.consumer.Watcher;
import com.jun.mqttx.entity.ClientSub;
import com.jun.mqttx.entity.ClientSubOrUnsubMsg;
import com.jun.mqttx.entity.InternalMessage;
import com.jun.mqttx.service.IInternalMessagePublishService;
import com.jun.mqttx.service.ISubscriptionService;
import com.jun.mqttx.utils.TopicUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主题订阅服务
 *
 * @author Jun
 * @date 2020-03-09 21:06
 */
@Slf4j
@Service
public class SubscriptionServiceImpl implements ISubscriptionService, Watcher<ClientSubOrUnsubMsg> {

    /**
     * 按顺序 -> 订阅，解除订阅，删除 topic
     */
    public static final int SUB = 1, UN_SUB = 2, DEL_TOPIC = 3;

    private StringRedisTemplate stringRedisTemplate;
    private IInternalMessagePublishService internalMessagePublishService;

    private Set<String> allTopics = ConcurrentHashMap.newKeySet();
    private Map<String, ConcurrentHashMap.KeySetView<ClientSub, Boolean>> topicClientMap = new ConcurrentHashMap<>();

    /**
     * 订阅主题前缀
     */
    private String topicPrefix;

    /**
     * 主题集合
     */
    private String topicSetKey;

    private Boolean enableInnerCache, enableCluster;
    private int brokerId;

    public SubscriptionServiceImpl(StringRedisTemplate stringRedisTemplate, BizConfig bizConfig,
                                   @Nullable IInternalMessagePublishService internalMessagePublishService) {
        Assert.notNull(stringRedisTemplate, "stringRedisTemplate can't be null");

        this.stringRedisTemplate = stringRedisTemplate;
        this.internalMessagePublishService = internalMessagePublishService;
        this.topicPrefix = bizConfig.getTopicPrefix();
        this.topicSetKey = bizConfig.getTopicSetKey();
        this.enableCluster = bizConfig.getEnableCluster();
        if (!enableCluster) {
            //如果用户没有设置，非集群状态下，默认开启缓存
            this.enableInnerCache = bizConfig.getEnableInnerCache() == null ? true : bizConfig.getEnableInnerCache();
        } else {
            //如果用户没有设置，集群状态下，默认关闭缓存
            this.enableInnerCache = bizConfig.getEnableInnerCache() == null ? false : bizConfig.getEnableInnerCache();
        }
        if (enableInnerCache) {
            initInnerCache(stringRedisTemplate);
        }else {
            allTopics = null;
            topicClientMap = null;
        }
        this.brokerId = bizConfig.getBrokerId();

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

        if (enableInnerCache) {
            subscribeWithCache(clientSub);

            //发布集群广播
            if (enableCluster) {
                InternalMessage<ClientSub> im = new InternalMessage<>(clientSub, System.currentTimeMillis(), brokerId);
                internalMessagePublishService.publish(im, InternalMessageEnum.SUB_UNSUB.getChannel());
            }
        }
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

        //启用内部缓存机制
        if (enableInnerCache) {
            unsubscribeWithCache(clientId, topics);

            //集群广播
            if (enableCluster) {
                ClientSubOrUnsubMsg clientSubOrUnsubMsg = new ClientSubOrUnsubMsg(clientId, 0, null, topics, UN_SUB);
                InternalMessage<ClientSubOrUnsubMsg> im = new InternalMessage<>(clientSubOrUnsubMsg, System.currentTimeMillis(), brokerId);
                internalMessagePublishService.publish(im, InternalMessageEnum.SUB_UNSUB.getChannel());
            }
        }
    }


    /**
     * 返回订阅主题的客户列表。考虑到 pub 类别的消息最为频繁且每次 pub 都会触发 <code>searchSubscribeClientList(String topic)</code>
     * 方法，所以增加内部缓存以优化该方法的执行逻辑。
     *
     * @param topic 主题
     * @return 客户ID列表
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<ClientSub> searchSubscribeClientList(String topic) {
        //启用内部缓存机制
        if (enableInnerCache) {
            return searchSubscribeClientListByCache(topic);
        }

        //未启用内部缓存机制，直接通过 redis 抓取
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

        if (enableInnerCache) {
            removeTopicWithCache(topic);

            //集群广播
            if (enableCluster) {
                ClientSubOrUnsubMsg clientSubOrUnsubMsg = new ClientSubOrUnsubMsg(null, 0, topic, null, DEL_TOPIC);
                InternalMessage<ClientSubOrUnsubMsg> im = new InternalMessage<>(clientSubOrUnsubMsg, System.currentTimeMillis(), brokerId);
                internalMessagePublishService.publish(im, InternalMessageEnum.SUB_UNSUB.getChannel());
            }
        }
    }

    @Override
    public void action(InternalMessage<ClientSubOrUnsubMsg> im) {
        if (enableInnerCache) {
            ClientSubOrUnsubMsg data = im.getData();
            int type = data.getType();
            switch (type) {
                case SUB:
                    subscribeWithCache(new ClientSub(data.getClientId(), data.getQos(), data.getTopic()));
                    break;
                case UN_SUB:
                    unsubscribeWithCache(data.getClientId(), data.getTopics());
                    break;
                case DEL_TOPIC:
                    removeTopicWithCache(data.getTopic());
                    break;
                default:
                    log.error("非法的 ClientSubOrUnsubMsg type:" + type);
            }
        }
    }

    @Override
    public boolean support(String channel) {
        return InternalMessageEnum.SUB_UNSUB.getChannel().equals(channel);
    }

    /**
     * 初始化内部缓存。目前的策略是全部加载，其实可以按需加载，按业务需求来吧。
     */
    private void initInnerCache(final StringRedisTemplate redisTemplate) {
        log.info("enableInnerCache=true, 开始加载缓存...");

        final Set<String> allTopic = redisTemplate.opsForSet().members(topicSetKey);
        if (!CollectionUtils.isEmpty(allTopic)) {
            allTopics.addAll(allTopic);

            allTopic.forEach(topic -> {
                Map<Object, Object> entries = redisTemplate.opsForHash().entries(topicPrefix + topic);
                if (!CollectionUtils.isEmpty(entries)) {
                    entries.forEach((k, v) -> {
                        String key = (String) k;
                        String val = (String) v;
                        ConcurrentHashMap.KeySetView<ClientSub, Boolean> orDefault = topicClientMap.getOrDefault(topic, ConcurrentHashMap.newKeySet());
                        orDefault.add(new ClientSub(key, Integer.parseInt(val), topic));
                    });
                }
            });
        } else {
            log.warn("redis 存储的 topic 列表为空");
        }

        log.info("缓存加载完成...");
    }

    /**
     * 通过缓存获取客户端订阅列表
     *
     * @param topic 主题
     * @return 客户端订阅列表
     */
    private List<ClientSub> searchSubscribeClientListByCache(String topic) {
        //result
        List<ClientSub> clientSubList = new ArrayList<>();

        for (String t : allTopics) {
            if (TopicUtils.match(topic, t)) {
                ConcurrentHashMap.KeySetView<ClientSub, Boolean> clientSubs = topicClientMap.get(t);
                clientSubList.addAll(clientSubs);
            }
        }
        return clientSubList;
    }

    /**
     * 移除 topic 缓存
     *
     * @param topic 主题
     */
    private void removeTopicWithCache(String topic) {
        allTopics.remove(topic);
        topicClientMap.remove(topic);
    }

    /**
     * 移除缓存中的订阅
     *
     * @param clientId 客户端ID
     * @param topics   主题列表
     */
    private void unsubscribeWithCache(String clientId, List<String> topics) {
        for (String topic : topics) {
            ConcurrentHashMap.KeySetView<ClientSub, Boolean> clientSubs = topicClientMap.get(topic);
            clientSubs.removeIf(clientSub -> Objects.equals(clientId, clientSub.getClientId()));
        }
    }

    /**
     * 将客户端订阅存储到缓存
     *
     * @param clientSub 客户端端订阅
     */
    private void subscribeWithCache(ClientSub clientSub) {
        String topic = clientSub.getTopic();

        allTopics.add(topic);

        //保存客户端订阅内容
        ConcurrentHashMap.KeySetView<ClientSub, Boolean> subMap = topicClientMap.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet());
        subMap.add(clientSub);
    }
}
