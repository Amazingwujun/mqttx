package com.jun.mqttx;

import com.jun.mqttx.broker.BrokerInitializer;
import com.jun.mqttx.config.MqttxConfig;
import com.jun.mqttx.exception.GlobalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Slf4j
@SpringBootApplication
public class MqttxApplication {

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext ctx = SpringApplication.run(MqttxApplication.class, args);

        // preCheck
        preCheck(ctx);

        //启动mqtt
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
        if (Boolean.TRUE.equals(mqttxConfig.getEnableTestMode())) {
            log.warn("mqttx 处于测试模式!!!");
            return;
        }

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
            if (Boolean.TRUE.equals(enableInnerCache) && StringUtils.isEmpty(innerCacheConsistencyKey)) {
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
