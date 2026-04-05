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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_CONSUMER_KEY;
import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_GROUP_KEY;
import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class ShortLinkStatsPendingReclaimSchedulerTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ShortLinkStatsSaveConsumer shortLinkStatsSaveConsumer;
    @Mock
    private StreamOperations<String, Object, Object> streamOperations;
    @Mock
    private MapRecord<String, String, String> mapRecord;

    private ShortLinkStatsPendingReclaimScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ShortLinkStatsPendingReclaimScheduler(stringRedisTemplate, shortLinkStatsSaveConsumer);
        ReflectionTestUtils.setField(scheduler, "reclaimBatchSize", 20L);
        ReflectionTestUtils.setField(scheduler, "reclaimMinIdleSeconds", 60L);
        when(stringRedisTemplate.opsForStream()).thenReturn(streamOperations);
    }

    @Test
    void reclaimPendingMessagesShouldClaimAndReconsumeWhenHasExpiredPendingMessage() {
        PendingMessagesSummary summary = new PendingMessagesSummary(
                SHORT_LINK_STATS_STREAM_GROUP_KEY,
                1,
                Range.closed("1-0", "1-0"),
                Map.of("other-consumer", 1L)
        );
        PendingMessage pendingMessage = new PendingMessage(
                RecordId.of("1-0"),
                Consumer.from(SHORT_LINK_STATS_STREAM_GROUP_KEY, "other-consumer"),
                Duration.ofSeconds(120),
                1
        );
        PendingMessages pendingMessages = new PendingMessages(
                SHORT_LINK_STATS_STREAM_GROUP_KEY,
                List.of(pendingMessage)
        );
        List<MapRecord<String, Object, Object>> claimedMessages =
                List.of((MapRecord<String, Object, Object>) (MapRecord<?, ?, ?>) mapRecord);

        when(streamOperations.pending(SHORT_LINK_STATS_STREAM_TOPIC_KEY, SHORT_LINK_STATS_STREAM_GROUP_KEY)).thenReturn(summary);
        when(streamOperations.pending(eq(SHORT_LINK_STATS_STREAM_TOPIC_KEY), eq(SHORT_LINK_STATS_STREAM_GROUP_KEY), any(Range.class), eq(20L)))
                .thenReturn(pendingMessages);
        doReturn(claimedMessages).when(streamOperations)
                .claim(eq(SHORT_LINK_STATS_STREAM_TOPIC_KEY), eq(SHORT_LINK_STATS_STREAM_GROUP_KEY), eq(SHORT_LINK_STATS_STREAM_CONSUMER_KEY), any(Duration.class), any(RecordId.class));

        scheduler.reclaimPendingMessages();

        verify(shortLinkStatsSaveConsumer).onMessage(mapRecord);
    }
}
