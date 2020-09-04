package com.jun.mqttx.consumer;

import com.jun.mqttx.constants.InternalMessageEnum;
import com.jun.mqttx.entity.InternalMessage;

/**
 * 观察者，实现此接口.
 *
 * @author Jun
 * @date 2020-05-14 09:15
 */
public interface Watcher<T> {

    /**
     * 每当有新的集群消息达到是，触发行为。注意：方法不允许出现阻塞操作
     *
     * @param im {@see InternalMessage}
     */
    void action(InternalMessage<T> im);

    /**
     * Watcher 支持的 channel 类别
     *
     * @param channel {@link InternalMessageEnum}
     * @return true if Watcher support
     */
    boolean support(String channel);
}
