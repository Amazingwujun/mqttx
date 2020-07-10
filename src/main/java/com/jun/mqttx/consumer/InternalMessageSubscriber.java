package com.jun.mqttx.consumer;

import com.alibaba.fastjson.JSON;
import com.jun.mqttx.common.config.BizConfig;
import com.jun.mqttx.entity.InternalMessage;
import org.springframework.util.Assert;

import java.util.List;

/**
 * 集群消息接收器，默认采用 redis 实现，这样不用引入多余的依赖。但是个人推荐后面结合公司自身的业务需求使用合适的 mq ，比如 KAFKA。
 *
 * @author Jun
 * @date 2020-05-14 09:17
 */
@SuppressWarnings("rawtypes")
public class InternalMessageSubscriber {

    private int brokerId;

    private List<Watcher> watchers;

    public InternalMessageSubscriber(List<Watcher> watchers, BizConfig bizConfig) {
        Assert.notNull(watchers, "watchers can't be null");
        Assert.notNull(bizConfig, "bizConfig can't be null");

        this.watchers = watchers;
        if (bizConfig.getEnableCluster()) {
            this.brokerId = bizConfig.getBrokerId();
        }
    }

    /**
     * 分发集群消息，当前处理类别：
     * <ol>
     *     <li>客户端连接断开 {@link com.jun.mqttx.common.constant.InternalMessageEnum#DISCONNECT}</li>
     *     <li>发布消息_qos012 {@link com.jun.mqttx.common.constant.InternalMessageEnum#PUB}</li>
     *     <li>发布消息响应_qos1 {@link com.jun.mqttx.common.constant.InternalMessageEnum#PUB_ACK}</li>
     *     <li>发布消息接收响应_qos2 {@link com.jun.mqttx.common.constant.InternalMessageEnum#PUB_REC}</li>
     *     <li>发布消息释放_qos2 {@link com.jun.mqttx.common.constant.InternalMessageEnum#PUB_REL}</li>
     *     <li>发布消息完成_qos2 {@link com.jun.mqttx.common.constant.InternalMessageEnum#PUB_COM}</li>
     *     <li>用户权限修改 {@link com.jun.mqttx.common.constant.InternalMessageEnum#ALTER_USER_AUTHORIZED_TOPICS}</li>
     * </ol>
     *
     * @param message 消息内容
     * @param channel 订阅频道
     */
    @SuppressWarnings("unchecked")
    public void handleMessage(String message, String channel) {
        //同 broker 消息屏蔽
        InternalMessage internalMessage = JSON.parseObject(message, InternalMessage.class);
        if (brokerId == internalMessage.getBrokerId()) {
            return;
        }

        for (Watcher watcher : watchers) {
            if (watcher.support(channel)) {
                watcher.action(JSON.parseObject(message, InternalMessage.class));
                //一个消息只能由一个观察者消费
                break;
            }
        }
    }
}
