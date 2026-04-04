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

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY;

/**
 * 短链接监控状态保存消息队列生产者（本地缓冲 + 批量 flush）
 */
@Component
@RequiredArgsConstructor
public class ShortLinkStatsSaveProducer {

    private final StringRedisTemplate stringRedisTemplate;

    private final ConcurrentLinkedQueue<Map<String, String>> statsBuffer = new ConcurrentLinkedQueue<>();

    /**
     * 统计记录入本地缓冲队列，由调度器批量 flush 到 Redis Stream
     */
    public void send(Map<String, String> producerMap) {
        statsBuffer.offer(producerMap);
    }

    /**
     * 将缓冲队列中所有记录批量写入 Redis Stream
     */
    public void flush() {
        if (statsBuffer.isEmpty()) {
            return;
        }
        List<Map<String, String>> batch = new ArrayList<>();
        Map<String, String> item;
        while ((item = statsBuffer.poll()) != null) {
            batch.add(item);
        }
        for (Map<String, String> record : batch) {
            stringRedisTemplate.opsForStream().add(SHORT_LINK_STATS_STREAM_TOPIC_KEY, record);
        }
    }
}
