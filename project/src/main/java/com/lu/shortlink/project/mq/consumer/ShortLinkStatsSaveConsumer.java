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

package com.lu.shortlink.project.mq.consumer;

import com.alibaba.fastjson2.JSON;
import com.lu.shortlink.project.common.convention.exception.ServiceException;
import com.lu.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.lu.shortlink.project.mq.idempotent.MessageQueueIdempotentHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_DEAD_LETTER_TOPIC_KEY;
import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_GROUP_KEY;
import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_RETRY_COUNT_KEY;

/**
 * Redis Stream 统计消费者。
 * short-link.stats.mq.type=redis-stream 时生效（默认）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "short-link.stats.mq.type", havingValue = "redis-stream", matchIfMissing = true)
@RequiredArgsConstructor
public class ShortLinkStatsSaveConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final StringRedisTemplate stringRedisTemplate;
    private final MessageQueueIdempotentHandler messageQueueIdempotentHandler;
    private final ShortLinkStatsHandler statsHandler;

    @Value("${short-link.stats.stream.max-retry-times:5}")
    private int maxRetryTimes;

    @Value("${short-link.stats.stream.retry-key-ttl-seconds:86400}")
    private long retryKeyTtlSeconds;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String stream = message.getStream();
        RecordId id = message.getId();
        if (messageQueueIdempotentHandler.isMessageBeingConsumed(id.toString())) {
            if (messageQueueIdempotentHandler.isAccomplish(id.toString())) {
                return;
            }
            throw new ServiceException("short-link stats message is in progress");
        }
        try {
            Map<String, String> producerMap = message.getValue();
            ShortLinkStatsRecordDTO statsRecord = JSON.parseObject(producerMap.get("statsRecord"), ShortLinkStatsRecordDTO.class);
            statsHandler.saveStats(statsRecord);
            stringRedisTemplate.opsForStream().acknowledge(Objects.requireNonNull(stream), SHORT_LINK_STATS_STREAM_GROUP_KEY, id);
            stringRedisTemplate.opsForStream().delete(stream, id);
            clearRetryState(id.toString());
            messageQueueIdempotentHandler.setAccomplish(id.toString());
        } catch (Throwable ex) {
            messageQueueIdempotentHandler.delMessageProcessed(id.toString());
            if (reachRetryLimit(id.toString())) {
                deadLetter(message, ex);
                stringRedisTemplate.opsForStream().acknowledge(Objects.requireNonNull(stream), SHORT_LINK_STATS_STREAM_GROUP_KEY, id);
                stringRedisTemplate.opsForStream().delete(stream, id);
                clearRetryState(id.toString());
                messageQueueIdempotentHandler.setAccomplish(id.toString());
                return;
            }
            log.error("Record short-link stats consume exception", ex);
            throw new ServiceException("short-link stats consume failed");
        }
    }

    private boolean reachRetryLimit(String messageId) {
        String retryCountKey = SHORT_LINK_STATS_STREAM_RETRY_COUNT_KEY + messageId;
        Long retryTimes = stringRedisTemplate.opsForValue().increment(retryCountKey);
        if (Objects.equals(retryTimes, 1L)) {
            stringRedisTemplate.expire(retryCountKey, retryKeyTtlSeconds, TimeUnit.SECONDS);
        }
        return retryTimes != null && retryTimes > maxRetryTimes;
    }

    private void clearRetryState(String messageId) {
        stringRedisTemplate.delete(SHORT_LINK_STATS_STREAM_RETRY_COUNT_KEY + messageId);
    }

    private void deadLetter(MapRecord<String, String, String> message, Throwable ex) {
        Map<String, String> deadLetterMessage = new HashMap<>(message.getValue());
        deadLetterMessage.put("messageId", message.getId().getValue());
        deadLetterMessage.put("sourceStream", message.getStream());
        deadLetterMessage.put("failedReason", ex == null ? "unknown" : String.valueOf(ex.getMessage()));
        deadLetterMessage.put("failedAt", String.valueOf(System.currentTimeMillis()));
        stringRedisTemplate.opsForStream().add(SHORT_LINK_STATS_STREAM_DEAD_LETTER_TOPIC_KEY, deadLetterMessage);
        log.error("Move stats message to dead letter stream. messageId={}", message.getId().getValue(), ex);
    }
}
