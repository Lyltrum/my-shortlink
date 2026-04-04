/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lu.shortlink.project.mq.producer;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 短链接统计缓冲定时 flush 调度器
 * 每 5 秒将本地缓冲队列中的统计记录批量写入 Redis Stream；
 * 应用关闭时通过 @PreDestroy 保证剩余记录不丢失。
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class ShortLinkStatsFlushScheduler {

    private final ShortLinkStatsSaveProducer shortLinkStatsSaveProducer;

    @Scheduled(fixedDelay = 5000)
    public void scheduledFlush() {
        shortLinkStatsSaveProducer.flush();
    }

    @PreDestroy
    public void shutdownFlush() {
        shortLinkStatsSaveProducer.flush();
    }
}
