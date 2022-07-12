/*
 * Copyright 2002-2020 the original author or authors.
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

import com.fasterxml.jackson.core.type.TypeReference;

import java.nio.charset.StandardCharsets;

/**
 * json 序列化处理器
 *
 * @since 1.0.7
 */
public class JsonSerializer implements Serializer {

    @Override
    public byte[] serialize(Object target) {
        return JSON.writeValueAsBytes(target);
    }

    @Override
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        return JSON.readValue(bytes, clazz);
    }

    public <T> T deserialize(byte[] bytes, TypeReference<T> typeReference) {
        return JSON.readValue(new String(bytes, StandardCharsets.UTF_8), typeReference);
    }
}
