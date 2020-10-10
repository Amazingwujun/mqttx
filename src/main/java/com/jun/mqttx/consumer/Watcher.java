package com.jun.mqttx.consumer;

import com.jun.mqttx.constants.InternalMessageEnum;

/**
 * 观察者，实现此接口.
 *
 * @author Jun
 * @since 1.0.4
 */
public interface Watcher {

    /**
     * 每当有新的集群消息达到是，触发行为。
     * 注意：实现方法不应该有耗时操作(e.g. 访问数据库)
     *
     * @param msg 集群消息
     */
    void action(String msg);

    /**
     * Watcher 支持的 channel 类别
     *
     * @param channel {@link InternalMessageEnum}
     * @return true if Watcher support
     */
    boolean support(String channel);
}