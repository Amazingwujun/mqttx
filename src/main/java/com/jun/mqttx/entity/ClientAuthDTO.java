package com.jun.mqttx.entity;

import lombok.Data;

import java.nio.charset.StandardCharsets;

/**
 * 客户端认证对象
 *
 * @author Jun
 * @since 1.0.5
 */
@Data
public class ClientAuthDTO {

    private String username;

    private String password;

    public static ClientAuthDTO of(String username, byte[] password) {
        ClientAuthDTO authDTO = new ClientAuthDTO();
        authDTO.setPassword(new String(password, StandardCharsets.UTF_8));
        authDTO.setUsername(username);
        return authDTO;
    }
}
