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

import com.lu.shortlink.project.common.convention.exception.ServiceException;
import com.lu.shortlink.project.dao.mapper.LinkAccessLogsMapper;
import com.lu.shortlink.project.dao.mapper.LinkAccessStatsMapper;
import com.lu.shortlink.project.dao.mapper.LinkBrowserStatsMapper;
import com.lu.shortlink.project.dao.mapper.LinkDeviceStatsMapper;
import com.lu.shortlink.project.dao.mapper.LinkLocaleStatsMapper;
import com.lu.shortlink.project.dao.mapper.LinkNetworkStatsMapper;
import com.lu.shortlink.project.dao.mapper.LinkOsStatsMapper;
import com.lu.shortlink.project.dao.mapper.LinkStatsTodayMapper;
import com.lu.shortlink.project.dao.mapper.ShortLinkGotoMapper;
import com.lu.shortlink.project.dao.mapper.ShortLinkMapper;
import com.lu.shortlink.project.mq.idempotent.MessageQueueIdempotentHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_GROUP_KEY;
import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_RETRY_COUNT_KEY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class ShortLinkStatsSaveConsumerTest {

    @Mock
    private ShortLinkMapper shortLinkMapper;
    @Mock
    private ShortLinkGotoMapper shortLinkGotoMapper;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private LinkAccessStatsMapper linkAccessStatsMapper;
    @Mock
    private LinkLocaleStatsMapper linkLocaleStatsMapper;
    @Mock
    private LinkOsStatsMapper linkOsStatsMapper;
    @Mock
    private LinkBrowserStatsMapper linkBrowserStatsMapper;
    @Mock
    private LinkAccessLogsMapper linkAccessLogsMapper;
    @Mock
    private LinkDeviceStatsMapper linkDeviceStatsMapper;
    @Mock
    private LinkNetworkStatsMapper linkNetworkStatsMapper;
    @Mock
    private LinkStatsTodayMapper linkStatsTodayMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private MessageQueueIdempotentHandler messageQueueIdempotentHandler;
    @Mock
    private StreamOperations<String, String, String> streamOperations;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private MapRecord<String, String, String> message;
    @Mock
    private RecordId recordId;

    private ShortLinkStatsSaveConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = spy(new ShortLinkStatsSaveConsumer(
                shortLinkMapper,
                shortLinkGotoMapper,
                redissonClient,
                linkAccessStatsMapper,
                linkLocaleStatsMapper,
                linkOsStatsMapper,
                linkBrowserStatsMapper,
                linkAccessLogsMapper,
                linkDeviceStatsMapper,
                linkNetworkStatsMapper,
                linkStatsTodayMapper,
                stringRedisTemplate,
                messageQueueIdempotentHandler
        ));
        ReflectionTestUtils.setField(consumer, "maxRetryTimes", 5);
        ReflectionTestUtils.setField(consumer, "retryKeyTtlSeconds", 86400L);
        lenient().when(stringRedisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);
        when(message.getStream()).thenReturn("short-link:stats-stream");
        when(message.getId()).thenReturn(recordId);
        when(recordId.toString()).thenReturn("1-0");
        lenient().when(recordId.getValue()).thenReturn("1-0");
        when(message.getValue()).thenReturn(Map.of("statsRecord", "{}"));
        when(messageQueueIdempotentHandler.isMessageBeingConsumed("1-0")).thenReturn(false);
    }

    @Test
    void onMessageShouldAcknowledgeAndDeleteWhenConsumeSuccess() {
        doNothing().when(consumer).actualSaveShortLinkStats(any());

        consumer.onMessage(message);

        verify(streamOperations).acknowledge("short-link:stats-stream", SHORT_LINK_STATS_STREAM_GROUP_KEY, recordId);
        verify(streamOperations).delete("short-link:stats-stream", recordId);
        verify(messageQueueIdempotentHandler).setAccomplish("1-0");
    }

    @Test
    void onMessageShouldThrowWhenConsumeFailedAndRetryNotExceed() {
        doThrow(new RuntimeException("boom")).when(consumer).actualSaveShortLinkStats(any());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(SHORT_LINK_STATS_STREAM_RETRY_COUNT_KEY + "1-0")).thenReturn(1L);

        assertThrows(ServiceException.class, () -> consumer.onMessage(message));

        verify(streamOperations, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
        verify(streamOperations, never()).delete(anyString(), any(RecordId.class));
        verify(streamOperations, never()).add(anyString(), anyMap());
        verify(messageQueueIdempotentHandler).delMessageProcessed("1-0");
    }

    @Test
    void onMessageShouldMoveToDeadLetterWhenRetryExceeded() {
        doThrow(new RuntimeException("boom")).when(consumer).actualSaveShortLinkStats(any());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(SHORT_LINK_STATS_STREAM_RETRY_COUNT_KEY + "1-0")).thenReturn(6L);

        assertDoesNotThrow(() -> consumer.onMessage(message));

        verify(streamOperations).add(anyString(), anyMap());
        verify(streamOperations).acknowledge("short-link:stats-stream", SHORT_LINK_STATS_STREAM_GROUP_KEY, recordId);
        verify(streamOperations).delete("short-link:stats-stream", recordId);
        verify(messageQueueIdempotentHandler).setAccomplish("1-0");
    }
}
