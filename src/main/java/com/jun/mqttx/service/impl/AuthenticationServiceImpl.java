package com.jun.mqttx.service.impl;

import com.jun.mqttx.entity.Authentication;
import com.jun.mqttx.service.IAuthenticationService;
import org.springframework.stereotype.Service;

/**
 * 认证服务
 *
 * @author Jun
 * @date 2020-03-04 12:44
 */
@Service
public class AuthenticationServiceImpl implements IAuthenticationService {

    @Override
    public Authentication authenticate(String username, byte[] password) {
        //do nothing

        return null;
    }
}
