package com.jun.mqttx.service.impl;

import com.jun.mqttx.entity.Authentication;
import com.jun.mqttx.entity.ClientAuthDTO;
import com.jun.mqttx.service.IAuthenticationService;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * 认证服务
 *
 * @author Jun
 * @since 1.0.4
 */
@Service
public class DefaultAuthenticationServiceImpl implements IAuthenticationService {

    @Override
    public void asyncAuthenticate(ClientAuthDTO authDTO, Consumer<Authentication> onResponse, Consumer<Throwable> onFailure) {
        onResponse.accept(null);
    }
}
