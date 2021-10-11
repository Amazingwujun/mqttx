package com.jun.mqttx.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HttpClient 配置
 *
 * @author Jun
 * @since 1.0.0
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public OkHttpClient okHttpClient(MqttxConfig config) {
        return new OkHttpClient.Builder()
                .connectTimeout(config.getAuth().getConnectTimeout())
                .readTimeout(config.getAuth().getReadTimeout())
                .build();
    }
}
