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
import com.jun.mqttx.exception.AuthenticationException;
import com.jun.mqttx.exception.AuthorizationException;

import java.util.function.Consumer;

/**
 * 客户端认证服务
 *
 * @author Jun
 * @since 1.0.4
 */
public interface IAuthenticationService {

    /**
     * 执行客户认证。同步请求阻塞 netty io 线程，此接口将在 1.0.5.RELEASE 版本删除
     *
     * @param username 用户名
     * @param password 密码
     * @throws AuthenticationException if authenticate failed
     * @throws AuthorizationException  if client
     * @deprecated 同步接口不建议使用，替代方法 {@link #asyncAuthenticate(ClientAuthDTO, Consumer, Consumer)}
     */
    @Deprecated
    Authentication authenticate(String username, byte[] password) throws AuthenticationException, AuthorizationException;

    /**
     * 异步认证，以 Okhttp 为例:
     * <pre>
     *     OkHttpClient client = new OkHttpClient();
     *
     *     Request request = new Request.Builder()
     *             .url("https://localhost/authenticate")
     *             .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), JSON.toJSONString(authDTO)))
     *             .build();
     *
     *     client.newCall(request).enqueue(new Callback() {
     *
     *         public void onFailure(Call call, IOException e) {
     *             onFailure.accept(e);
     *         }
     *
     *         public void onResponse(Call call, Response response) throws IOException {
     *             Authentication auth = JSON.parseObject(response.body().string(), Authentication.class);
     *             onResponse.accept(auth);
     *         }
     *     });
     * </pre>
     *
     * @param authDTO    {@link ClientAuthDTO} 客户端认证对象
     * @param onResponse 响应成功后执行
     * @param onFailure  请求失败后响应
     */
    void asyncAuthenticate(ClientAuthDTO authDTO, Consumer<Authentication> onResponse, Consumer<Throwable> onFailure);
}

