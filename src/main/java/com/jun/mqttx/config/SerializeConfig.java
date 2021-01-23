package com.jun.mqttx.config;

import com.jun.mqttx.constants.SerializeStrategy;
import com.jun.mqttx.utils.JsonSerializer;
import com.jun.mqttx.utils.KryoSerializer;
import com.jun.mqttx.utils.Serializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SerializeConfig {

    @Bean
    @ConditionalOnProperty(prefix = "mqttx", name = "serialize-strategy", havingValue = SerializeStrategy.JSON)
    public Serializer jsonSerializer() {
        return new JsonSerializer();
    }

    @Bean
    @ConditionalOnProperty(prefix = "mqttx", name = "serialize-strategy", havingValue = SerializeStrategy.KRYO)
    public Serializer kryoSerializer() {
        return new KryoSerializer();
    }
}
