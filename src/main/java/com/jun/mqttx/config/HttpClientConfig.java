package com.jun.mqttx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

/**
 * httClient 配置
 *
 * @author Jun
 * @since 1.0.0
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient httpClient(MqttxConfig config) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(config.getHttpClientConnectTimeout())
                .build();
    }
}
