package com.jun.mqttx.utils;

import com.jun.mqttx.config.BizConfig;
import com.jun.mqttx.exception.SslException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * 用于加载生成 {@link KeyManagerFactory} 及 {@link TrustManagerFactory}
 *
 * @author Jun
 * @date 2020-03-03 21:19
 */
@Component
public class SslUtils {

    private KeyStore keyStore;

    private String storePassword;

    public SslUtils(BizConfig bizConfig, ResourceLoader resourceLoader) throws IOException, KeyStoreException,
            CertificateException, NoSuchAlgorithmException {
        BizConfig.Ssl ssl = bizConfig.getSsl();
        if (!Boolean.TRUE.equals(ssl.getEnable())) {
            return;
        }

        storePassword = ssl.getKeyStorePassword();
        Assert.hasText(storePassword, "keystore password can't be null");

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
