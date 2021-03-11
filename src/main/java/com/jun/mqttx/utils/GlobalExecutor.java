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

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 全局执行器，相关配置:
 * <ol>
 *     <li>最低执行线程数量: {@link Runtime#availableProcessors()}</li>
 *     <li>最大线程池数量: {@link Runtime#availableProcessors()} * 2 </li>
 *     <li>空闲时线程存活时间: 60 秒</li>
 *     <li>任务队列: {@link ArrayBlockingQueue}</li>
 *     <li>拒绝策略: 抛弃任务并打印错误信息</li>
 * </ol>
 *
 * @since 1.0.7
 */
@Slf4j
public class GlobalExecutor {
    //@formatter:off
    /** 任务队列大小 */
    private static final int queueSize = 200;
    private static final ThreadPoolExecutor taskConsumer;
    //@formatter:on

    static {
        int coreSize = Runtime.getRuntime().availableProcessors();
        taskConsumer = new ThreadPoolExecutor(
                coreSize,
                coreSize << 2,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize),
                (r, executor) -> log.error(String.format("待处理任务数量已达[%s]，任务[%s]将被抛弃", queueSize, ((DefaultTask) r).taskName))
        );
    }

    /**
     * 通过线程池执行任务
     *
     * @param taskName 任务名称，用于标志任务
     * @param r        {@link Runnable}
     */
    public static void execute(String taskName, Runnable r) {
        taskConsumer.execute(new DefaultTask(taskName, r));
        if (log.isDebugEnabled()) {
            log.debug("活跃线程数:{} 最大线程数:{} 待处理任务队列size:{}",
                    taskConsumer.getActiveCount(),
                    taskConsumer.getMaximumPoolSize(),
                    taskConsumer.getQueue().size()
            );
        }
    }

    private static final class DefaultTask implements Runnable {

        public final String taskName;
        private final Runnable r;

        public DefaultTask(String taskName, Runnable r) {
            this.taskName = taskName;
            this.r = r;
        }

        @Override
        public void run() {
            r.run();
        }
    }
}
