package com.jun.mqttx.constants;

import lombok.extern.slf4j.Slf4j;

/**
 * 共享订阅策略, 分别支持哈希、随机、轮询机制.
 *
 * @author Jun
 * @since 1.0.4
 */
@Slf4j
public enum ShareStrategy {
    hash,
    random,
    round;
}