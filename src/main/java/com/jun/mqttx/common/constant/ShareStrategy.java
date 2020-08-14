package com.jun.mqttx.common.constant;

import lombok.extern.slf4j.Slf4j;

/**
 * 共享订阅策略, 分别支持哈希、随机、轮询机制.
 *
 * @author Jun
 * @date 2020-08-11 15:14
 */
@Slf4j
public enum ShareStrategy {
    hash,
    random,
    round;

    /**
     * 返回处理共享订阅处理策略
     *
     * @param strategy 策略
     * @return 支持的策略
     */
    public static ShareStrategy getStrategy(String strategy) {
        ShareStrategy shareStrategy;
        try {
            shareStrategy = valueOf(strategy);
        } catch (IllegalArgumentException e) {
            return random;
        }

        return shareStrategy;
    }
}
