package com.jun.mqttx.service;

import java.util.List;

/**
 * 订阅相关服务
 *
 * @author Jun
 * @date 2020-03-09 21:03
 */
public interface ISubscriptionService {

    /**
     * 保存客户订阅的主题
     *
     * @param clientId 客户id
     * @param topics   订阅主题列表
     */
    void subscribe(String clientId, List<String> topics);

    /**
     * 解除订阅
     *
     * @param clientId 客户id
     * @param topics   主题列表
     */
    void unsubscribe(String clientId, List<String> topics);

    /**
     * 获取订阅了 topic 的客户id
     *
     * @param topic 主题
     * @return 订阅了主题的客户id列表
     */
    List<String> searchSubscribeClientList(String topic);

    /**
     * 移除客户订阅
     *
     * @param clientId 客户ID
     */
    void clearClientSubscriptions(String clientId);

    /**
     * 移除指定 topic
     *
     * @param topic 主题
     */
    void removeTopic(String topic);
}
