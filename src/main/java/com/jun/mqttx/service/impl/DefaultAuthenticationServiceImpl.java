package com.jun.mqttx.service.impl;

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.entity.Authentication;
import com.jun.mqttx.entity.ClientAuthDTO;
import com.jun.mqttx.exception.AuthenticationException;
import com.jun.mqttx.service.IAuthenticationService;
import com.jun.mqttx.utils.JSON;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;

/**
 * 认证服务
 *
 * @author Jun
 * @since 1.0.4
 */
@Slf4j
@Service
public class DefaultAuthenticationServiceImpl implements IAuthenticationService {

    private final OkHttpClient okHttpClient;
    private final URL url;

    public DefaultAuthenticationServiceImpl(MqttxConfig config, OkHttpClient okHttpClient) {
        MqttxConfig.Auth auth = config.getAuth();
        String url = auth.getUrl();
        if (ObjectUtils.isEmpty(url)) {
            this.url = null;
        } else {
            URI uri = URI.create(url);
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
            try {
                this.url = uri.toURL();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(format("非法的 url[%s]", url));
            }
        }
        this.okHttpClient = okHttpClient;
    }

    @Override
    public CompletableFuture<Authentication> asyncAuthenticate(ClientAuthDTO authDTO) {
        if (url != null) {
            CompletableFuture<Authentication> future = new CompletableFuture<>();
            Request req = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(MediaType.get("application/json"), JSON.writeValueAsString(authDTO)))
                    .build();
            okHttpClient.newCall(req)
                    .enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            future.completeExceptionally(e);
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            int code = response.code();
                            ResponseBody body = response.body();
                            if (code == 200) {
                                if (body == null) {
                                    future.complete(null);
                                } else {
                                    Authentication authentication = JSON.readValue(body.string(), Authentication.class);
                                    future.complete(authentication);
                                }
                            } else {
                                future.completeExceptionally(
                                        new AuthenticationException(
                                                format(
                                                        "认证失败 statusCode[%s], 原因:[%s]",
                                                        code,
                                                        Optional.ofNullable(body).map(e -> {
                                                            try {
                                                                return e.string();
                                                            } catch (IOException ex) {
                                                                log.error(ex.getMessage(), ex);
                                                                return ex.getMessage();
                                                            }
                                                        }).orElse("Unknown")
                                                )
                                        )
                                );
                            }
                        }
                    });
            return future;
        } else {
            return CompletableFuture.supplyAsync(() -> null);
        }
    }
}
