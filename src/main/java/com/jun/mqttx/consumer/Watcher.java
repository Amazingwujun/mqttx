package com.jun.mqttx.consumer;

import com.jun.mqttx.entity.InternalMessage;

/**
 * 观察者，实现此接口
 *
 * @author Jun
 * @date 2020-05-14 09:15
 */
public interface Watcher<T> {

    /**
     * 每当有新的集群消息达到是，触发行为
     *
     * @param im {@see InternalMessage}
     */
    void action(InternalMessage<T> im);

    /**
     * 获取观察者具体的观测 channel
     *
     * @return channel
     */
    String channel();
}
