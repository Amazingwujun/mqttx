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

package com.jun.mqttx.service;

import com.jun.mqttx.entity.Authentication;
import com.jun.mqttx.entity.ClientAuthDTO;

import java.util.concurrent.CompletableFuture;

/**
 * 客户端认证服务
 *
 * @author Jun
 * @since 1.0.4
 */
public interface IAuthenticationService {

    /**
     * 异步认证.
     * <p>
     * mqttx 将由 JDK8 -> JDK17, 故采用jdk原生 HttpClient 替代 Okhttp
     * </p>
     *
     * @param authDTO {@link ClientAuthDTO} 客户端认证对象
     */
    CompletableFuture<Authentication> asyncAuthenticate(ClientAuthDTO authDTO);
}

