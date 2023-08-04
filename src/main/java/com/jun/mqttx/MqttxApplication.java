/*
 * Copyright 2020-2023 the original author or authors.
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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;

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
@SpringBootApplication(exclude = RedisRepositoriesAutoConfiguration.class)
public class MqttxApplication {

    public static void main(String[] args) {
        SpringApplication.run(MqttxApplication.class, args);
    }

}
