package com.jun.mqttx.entity;

import lombok.Data;

/**
 * 原始 pub 消息 payload
 */
@Data
public class SourcePayload {
    //@formatter:off

    /** payload id */
    private String id;
    private byte[] payload;

    public static SourcePayload of(String id,byte[] payload){
        SourcePayload sourcePayload = new SourcePayload();
        sourcePayload.setId(id);
        sourcePayload.setPayload(payload);
        return sourcePayload;
    }
}
