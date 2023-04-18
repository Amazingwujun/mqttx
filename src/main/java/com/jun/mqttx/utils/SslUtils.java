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

import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.exception.SslException;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

/**
 * 用于加载生成 {@link KeyManagerFactory} 及 {@link TrustManagerFactory}
 *
 * @author Jun
 * @since 1.0.4
 */
@Component
@Slf4j
public class SslUtils {

    private KeyStore keyStore;

    private String storePassword;

    public SslUtils(MqttxConfig mqttxConfig, ResourceLoader resourceLoader) throws IOException, KeyStoreException,
            CertificateException, NoSuchAlgorithmException {
        MqttxConfig.Ssl ssl = mqttxConfig.getSsl();
        if (!Boolean.TRUE.equals(ssl.getEnable())) {
            return;
        }

        storePassword = ssl.getKeyStorePassword();

        Resource pk = resourceLoader.getResource(ssl.getKeyStoreLocation());
        keyStore = KeyStore.getInstance(ssl.getKeyStoreType());
        keyStore.load(pk.getInputStream(), storePassword.toCharArray());
    }


    /**
     * 获取 TrustManagerFactory
     *
     * @return {@link TrustManagerFactory}
     */
    public TrustManagerFactory getTrustManagerFactory() {
        TrustManagerFactory trustManagerFactory;
        try {
            trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
            trustManagerFactory.init(keyStore);
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new SslException("trustManagerFactory 加载失败");
        }

        return trustManagerFactory;
    }

    /**
     * 获取 keyManagerFactory
     *
     * @return {@link KeyManagerFactory}
     */
    public KeyManagerFactory getKeyManagerFactory() {
        KeyManagerFactory keyManagerFactory;
        try {
            keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            keyManagerFactory.init(keyStore, storePassword.toCharArray());
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException e) {
            throw new SslException("KeyManagerFactory 加载失败");
        }
        return keyManagerFactory;
    }

}
