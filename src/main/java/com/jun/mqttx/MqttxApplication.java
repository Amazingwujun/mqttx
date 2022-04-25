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

package com.jun.mqttx;

import com.jun.mqttx.broker.BrokerInitializer;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.exception.GlobalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.ObjectUtils;

import java.util.Objects;

/**
 * 项目地址:
 * <ul>
 *     <li><a href="https://github.com/Amazingwujun/mqttx">github</a></li>
 *     <li><a href="https://gitee.com/amazingJun/mqttx">gitee</a></li>
 * </ul>
 * 如果项目对你有所帮助，就帮作者 <i>star</i> 一下吧😊
 *
 * @author Jun
 */
@Slf4j
@SpringBootApplication(exclude = RedisRepositoriesAutoConfiguration.class)
public class MqttxApplication {

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext ctx = SpringApplication.run(MqttxApplication.class, args);

        // preCheck
        preCheck(ctx);

        // 启动mqtt
        ctx.getBean(BrokerInitializer.class).start();
    }

    /**
     * 服务启动前一些状态检查，包括：
     * <ol>
     *     <li>集群内部缓存一致性</li>
     * </ol>
     *
     * @param ctx {@link ConfigurableApplicationContext}
     */
    private static void preCheck(ApplicationContext ctx) {
        MqttxConfig mqttxConfig = ctx.getBean(MqttxConfig.class);
        log.info("开始自检...");

        innerCacheConsistencyCheck(ctx);

        log.info("自检完成...");
    }

    /**
     * 检查集群内部缓存开启状态是否一致
     *
     * @param ctx {@link ApplicationContext}
     */
    private static void innerCacheConsistencyCheck(ApplicationContext ctx) {
        MqttxConfig mqttxConfig = ctx.getBean(MqttxConfig.class);
        MqttxConfig.Cluster cluster = mqttxConfig.getCluster();

        Boolean enableCluster = cluster.getEnable();
        Integer brokerId = mqttxConfig.getBrokerId();
        if (Boolean.TRUE.equals(enableCluster)) {
            Boolean enableInnerCache = mqttxConfig.getEnableInnerCache();
            String innerCacheConsistencyKey = cluster.getInnerCacheConsistencyKey();
            if (Boolean.TRUE.equals(enableInnerCache) && ObjectUtils.isEmpty(innerCacheConsistencyKey)) {
                throw new IllegalArgumentException("mqttx.cluster.innerCacheConsistencyKey 值不能为空");
            }
            StringRedisTemplate redisTemplate = ctx.getBean(StringRedisTemplate.class);
            String clusterCacheStatus = redisTemplate.opsForValue().get(innerCacheConsistencyKey);
            if (clusterCacheStatus == null) {
                if (brokerId == null) {
                    throw new GlobalException("集群必须配置 brokerId");
                }

                log.info("内部缓存状态不存在，mqttx broker:{} 为集群第一个应用，内部缓存状态为:{}，后续加入的 mqttx 状态必须一致。",
                        brokerId, enableInnerCache ? "开" : "关");
                redisTemplate.opsForValue().set(innerCacheConsistencyKey, String.valueOf(enableInnerCache));
            } else {
                if (Objects.equals(clusterCacheStatus, String.valueOf(enableInnerCache))) {
                    log.info("自检->集群缓存状态：{}", enableInnerCache ? "开" : "关");
                } else {
                    throw new IllegalArgumentException("mqttx 集群状态 mqttx.enableInnerCache 不一致, 这会导致集群整体消息不一致!");
                }
            }
        }
    }
}
