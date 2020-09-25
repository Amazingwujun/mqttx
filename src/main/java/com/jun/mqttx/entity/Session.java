package com.jun.mqttx.entity;

import io.netty.handler.codec.mqtt.MqttPublishMessage;
import lombok.Data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * MQTT 会话
 *
 * @author Jun
 * @since 1.0.4
 */
@Data
public class Session {

    public static final String KEY = "session";

    /**
     * 客户ID
     */
    private String clientId;

    /**
     * 清理会话标志
     */
    private Boolean cleanSession;

    /**
     * 用于 cleanSession 连接，负责存储 qos > 0 的消息
     */
    private transient Map<Integer, PubMsg> pubMsgStore;

    /**
     * @see #pubMsgStore
     */
    private transient Set<Integer> pubRelMsgStore;

    /**
     * 遗嘱消息
     */
    private transient MqttPublishMessage willMessage;

    /**
     * 用于生成 msgId
     */
    private int messageId;

    private Session() {
    }

    /**
     * 创建会话
     *
     * @param clientId     客户端 id
     * @param cleanSession clean session 标识. true: 1; false: 0
     * @return Session for clean session = 1
     */
    public static Session of(String clientId, boolean cleanSession) {
        Session session = new Session();
        session.setClientId(clientId);
        session.setCleanSession(cleanSession);
        if (cleanSession) {
            session.setPubMsgStore(new HashMap<>());
            session.setPubRelMsgStore(new HashSet<>());
        }
        return session;
    }

    /**
     * session 绑定 channel, 而 channel 绑定 EventLoop 线程，这个方法是线程安全的（如果没有额外的配置）。
     *
     * @return {@link #messageId}
     */
    public int increaseAndGetMessageId() {
        return ++messageId;
    }

    /**
     * 清理遗嘱消息
     */
    public void clearWillMessage() {
        willMessage = null;
    }

    /**
     * 保存 {@link PubMsg}
     *
     * @param messageId 消息id
     * @param pubMsg    {@link PubMsg}
     */
    public void savePubMsg(Integer messageId, PubMsg pubMsg) {
        if (cleanSession) {
            pubMsgStore.put(messageId, pubMsg);
        }
    }

    /**
     * 移除 {@link PubMsg}
     *
     * @param messageId 消息id
     */
    public void removePubMsg(int messageId) {
        if (cleanSession) {
            pubMsgStore.remove(messageId);
        }
    }

    /**
     * 保存 {@link PubRelMsg}
     *
     * @param messageId 消息id
     */
    public void savePubRelMsg(int messageId) {
        if (cleanSession) {
            pubRelMsgStore.add(messageId);
        }
    }

    /**
     * 移除 {@link PubRelMsg}
     *
     * @param messageId 消息id
     */
    public void removePubRelMsg(int messageId) {
        if (cleanSession) {
            pubRelMsgStore.remove(messageId);
        }
    }
}