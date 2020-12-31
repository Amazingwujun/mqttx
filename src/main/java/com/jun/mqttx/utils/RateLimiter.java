package com.jun.mqttx.utils;

/**
 * 限流器, 基于令牌桶算法。
 */
public class RateLimiter {
    //@formatter:off

    private long lastRefreshed, lastTokens = -1;
    /** 令牌桶容量 */
    private final long capacity;
    /** 令牌补充速度 */
    private final int replenishRate;
    /** 每次请求需要消耗多少个令牌 */
    private final int tokenConsumedPerAcquire;

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
