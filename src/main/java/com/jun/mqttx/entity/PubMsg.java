package com.jun.mqttx.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 发布的消息
 *
 * @author Jun
 * @date 2020-03-09 22:02
 */
@Data
@AllArgsConstructor
public class PubMsg {

    /**
     * <ol>
     *     <li>0 -> at most once</li>
     *     <li>1 -> at least once</li>
     *     <li>2 -> exactly once</li>
     * </ol>
     */
    private int qoS;

    private int messageId;

    private String topic;

    private byte[] payload;
}
