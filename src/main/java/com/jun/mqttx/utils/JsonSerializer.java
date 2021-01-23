package com.jun.mqttx.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;

import java.nio.charset.StandardCharsets;

/**
 * json 序列化处理器
 */
public class JsonSerializer implements Serializer {

    @Override
    public byte[] serialize(Object target) {
        return JSON.toJSONBytes(target);
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        return JSON.parseObject(bytes, clazz);
    }

    public <T> T deserialize(byte[] bytes, TypeReference<T> typeReference) {
        return JSON.parseObject(new String(bytes, StandardCharsets.UTF_8), typeReference);
    }
}
