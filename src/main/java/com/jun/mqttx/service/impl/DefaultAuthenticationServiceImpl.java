package com.jun.mqttx.service.impl;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.entity.Authentication;
import com.jun.mqttx.entity.ClientAuthDTO;
import com.jun.mqttx.exception.AuthenticationException;
import com.jun.mqttx.service.IAuthenticationService;
import com.jun.mqttx.utils.JSON;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;

/**
 * 认证服务, 提供最基础的认证服务.
 * <p>
 * 当用户指定了配置项 mqttx.auth.url, 该对象使用 {@link HttpClient} 发出 POST 请求给 mqttx.auth.url。
 * 注意：接口返回 http status = 200 即表明认证成功, 其它状态值一律为认证失败
 * </p>
 *
 * @author Jun
 * @since 1.0.4
 */
@Service
public class DefaultAuthenticationServiceImpl implements IAuthenticationService {

    private final HttpClient httpClient;
    private final URI uri;
    private final Duration timeout;

    /**
     * 创建一个新的实例
     *
     * @param config     配置项
     * @param httpClient {@link HttpClient}
     */
    public DefaultAuthenticationServiceImpl(MqttxConfig config, HttpClient httpClient) {
        var url = config.getAuth().getUrl();
        timeout = config.getAuth().getTimeout();
        if (!ObjectUtils.isEmpty(url)) {
            uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null)
                throw new IllegalArgumentException(format("mqttx.auth.url[%s] 无效", url));
            scheme = scheme.toLowerCase(Locale.US);
            if (!(scheme.equals("https") || scheme.equals("http"))) {
                throw new IllegalArgumentException(format("mqttx.auth.url[%s] 的 scheme 无效", uri));
            }
            if (uri.getHost() == null) {
                throw new IllegalArgumentException(format("不支持的 mqttx.auth.url[%s]", uri));
            }
        } else {
            uri = null;
        }
        this.httpClient = httpClient;
    }

    @Override
    public CompletableFuture<Authentication> asyncAuthenticate(ClientAuthDTO authDTO) {
        if (uri == null) {
            return CompletableFuture.supplyAsync(() -> null);
        } else {
            var httpRequest = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(timeout)
                    .header("Content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(authDTO)))
                    .build();
            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(resp -> {
                        if (resp.statusCode() != 200) {
                            throw new AuthenticationException(format("认证失败 statusCode[%s], 原因:[%s]", resp.statusCode(), resp.body()));
                        }

                        return resp.body();
                    })
                    .thenApply(e -> JSON.readValue(e, Authentication.class));
        }
    }
}
