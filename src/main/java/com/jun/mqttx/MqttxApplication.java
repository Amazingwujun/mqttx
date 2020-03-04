package com.jun.mqttx;

import com.jun.mqttx.server.BrokerInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class MqttxApplication {

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext ctx = SpringApplication.run(MqttxApplication.class, args);

        //启动mqtt
        ctx.getBean(BrokerInitializer.class).start();
    }
}
