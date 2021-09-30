/*
 * Copyright 2002-2020 the original author or authors.
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

/**
 * 限流器, 基于令牌桶算法。
 *
 * @since 1.0.7
 */
public class RateLimiter {
    //@formatter:off

    /** 令牌桶容量 */
    private final long capacity;
    /** 令牌补充速度 */
    private final int replenishRate;
    /** 每次请求需要消耗多少个令牌 */
    private final int tokenConsumedPerAcquire;
    private long lastRefreshed, lastTokens = -1;

    //@formatter:on

    public RateLimiter(int capacity, int replenishRate, int tokenConsumedPerAcquire) {
        this.capacity = capacity;
        this.replenishRate = replenishRate;
        this.tokenConsumedPerAcquire = tokenConsumedPerAcquire;
    }

    /**
     * 获取令牌，实现思路源自 SpringGateway <code>RedisRateLimiter</code>.
     * <p/>
     * 相关资料参见：<a href="https://stripe.com/blog/rate-limiters">Scaling your API with rate limiters</a>
     *
     * @param acquireTime 请求时间,单位:秒
     * @return true 如果用户令牌桶中有可用令牌的话
     */
    public synchronized boolean acquire(long acquireTime) {
        if (lastTokens == -1) {
            lastTokens = capacity;
        }

        long delta = Math.max(0, acquireTime - lastRefreshed);
        long nowToken = Math.min(capacity, lastTokens + (delta * replenishRate));
        boolean allowed = nowToken >= tokenConsumedPerAcquire;
        if (allowed) {
            nowToken -= tokenConsumedPerAcquire;
        }

        lastRefreshed = acquireTime;
        lastTokens = nowToken;

        return allowed;
    }
}
