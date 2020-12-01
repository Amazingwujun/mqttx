package com.jun.mqttx.utils;


import java.util.HashMap;
import java.util.Map;

/**
 * 提供 messageId 生成工具, 供测试模式使用
 */
public class MessageIdUtils {

    private static final Map<String, Integer> map = new HashMap<>();

    public synchronized static int nextMessageId(String clientId) {
        Integer id = map.computeIfAbsent(clientId, k -> 1);
        if ((id & 0xffff) == 0) {
            id++;
        }
        map.put(clientId, id);
        return id;
    }
}
