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

