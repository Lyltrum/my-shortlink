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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_CONSUMER_KEY;
import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_GROUP_KEY;
import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY;

/**
 * 回收 Redis Stream PEL 中长时间未确认的消息，避免消息永久堆积。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "short-link.stats.mq.type", havingValue = "redis-stream", matchIfMissing = true)
@RequiredArgsConstructor
public class ShortLinkStatsPendingReclaimScheduler {

    private final StringRedisTemplate stringRedisTemplate;
    private final ShortLinkStatsSaveConsumer shortLinkStatsSaveConsumer;

    @Value("${short-link.stats.stream.reclaim-batch-size:20}")
    private long reclaimBatchSize;

    @Value("${short-link.stats.stream.reclaim-min-idle-seconds:60}")
    private long reclaimMinIdleSeconds;

    @Scheduled(fixedDelayString = "${short-link.stats.stream.reclaim-fixed-delay-millis:15000}")
    public void reclaimPendingMessages() {
        StreamOperations<String, Object, Object> streamOps = stringRedisTemplate.opsForStream();
        PendingMessagesSummary pendingSummary = streamOps.pending(SHORT_LINK_STATS_STREAM_TOPIC_KEY, SHORT_LINK_STATS_STREAM_GROUP_KEY);
        if (pendingSummary == null || pendingSummary.getTotalPendingMessages() <= 0) {
            return;
        }

        PendingMessages pendingMessages = streamOps.pending(
                SHORT_LINK_STATS_STREAM_TOPIC_KEY,
                SHORT_LINK_STATS_STREAM_GROUP_KEY,
                Range.unbounded(),
                reclaimBatchSize
        );
        if (pendingMessages == null || pendingMessages.isEmpty()) {
            return;
        }

        Duration minIdleTime = Duration.ofSeconds(reclaimMinIdleSeconds);
        List<RecordId> toClaimRecordIds = new ArrayList<>();
        for (PendingMessage each : pendingMessages) {
            if (each.getElapsedTimeSinceLastDelivery().compareTo(minIdleTime) >= 0) {
                toClaimRecordIds.add(each.getId());
            }
        }
        if (toClaimRecordIds.isEmpty()) {
            return;
        }

        List<MapRecord<String, Object, Object>> claimedMessages = streamOps.claim(
                SHORT_LINK_STATS_STREAM_TOPIC_KEY,
                SHORT_LINK_STATS_STREAM_GROUP_KEY,
                SHORT_LINK_STATS_STREAM_CONSUMER_KEY,
                minIdleTime,
                toClaimRecordIds.toArray(new RecordId[0])
        );
        if (claimedMessages == null || claimedMessages.isEmpty()) {
            return;
        }

        for (MapRecord<String, Object, Object> claimedMessage : claimedMessages) {
            try {
                @SuppressWarnings("unchecked")
                MapRecord<String, String, String> message = (MapRecord<String, String, String>) (MapRecord<?, ?, ?>) claimedMessage;
                shortLinkStatsSaveConsumer.onMessage(message);
            } catch (Throwable ex) {
                log.error("Reclaim pending stats message failed, messageId={}", claimedMessage.getId().getValue(), ex);
            }
        }
    }
}

