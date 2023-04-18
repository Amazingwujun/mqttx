/*
 * Copyright 2020-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jun.mqttx.utils;

/**
 * 序列化接口
 *
 * @since 1.0.7
 */
public interface Serializer {

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
