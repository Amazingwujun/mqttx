package com.jun.mqttx.utils;

/**
 * 序列化接口
 */
public interface Serializer {

    /**
     * 与 {@link io.netty.channel.Channel} 关联的 key
     */
    String Key = "serializer";

    /**
     * 将对象序列化成字节
     *
     * @param target 待序列化实列
     * @return 序列化后字节数组
     */
    byte[] serialize(Object target);

    /**
     * 将字节数组反序列化为 T 类型的对象
     *
     * @param bytes 对象字节数组
     * @param clazz 反序列化对象类型
     * @return T
     */
    <T> T deserialize(byte[] bytes, Class<T> clazz);
}
