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

package com.jun.mqttx.entity;

import lombok.Data;

import java.util.List;

/**
 * 认证响应对象.
 *
 * @author Jun
 * @since 1.0.4
 */
@Data
public class Authentication {

    /**
     * 允许订阅的 topic filter
     */
    private List<String> authorizedSub;

    /**
     * 允许发布的 topic filter
     */
    private List<String> authorizedPub;

    /**
     * 客户端id
     */
    private String clientId;

    public static Authentication of(String clientId) {
        var auth = new Authentication();
        auth.clientId = clientId;
        return auth;
    }
}

    private String password;
}
