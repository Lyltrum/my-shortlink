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
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * RocketMQ 统计消费者。
 * short-link.stats.mq.type=rocketmq 时生效。
 * 重试由 RocketMQ Broker 自动处理（最多 maxReconsumeTimes 次），超限后消息进入 DLQ。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "short-link.stats.mq.type", havingValue = "rocketmq")
@RocketMQMessageListener(
        topic = "short-link-stats-topic",
        consumerGroup = "short-link-stats-consumer-group",
        maxReconsumeTimes = 5
)
@RequiredArgsConstructor
public class RocketMQStatsSaveConsumer implements RocketMQListener<MessageExt> {

    private final ShortLinkStatsHandler statsHandler;
    private final MessageQueueIdempotentHandler messageQueueIdempotentHandler;

    @Override
    public void onMessage(MessageExt message) {
        String msgId = message.getMsgId();
        if (messageQueueIdempotentHandler.isMessageBeingConsumed(msgId)) {
            if (messageQueueIdempotentHandler.isAccomplish(msgId)) {
                return;
            }
            throw new ServiceException("short-link stats message is in progress");
        }
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            ShortLinkStatsRecordDTO statsRecord = JSON.parseObject(body, ShortLinkStatsRecordDTO.class);
            statsHandler.saveStats(statsRecord);
            messageQueueIdempotentHandler.setAccomplish(msgId);
        } catch (Throwable ex) {
            messageQueueIdempotentHandler.delMessageProcessed(msgId);
            // 抛出异常让 RocketMQ Broker 触发重试；超过 maxReconsumeTimes 后自动入 DLQ
            log.error("RocketMQ stats consume failed, msgId={}", msgId, ex);
            throw ex;
        }
    }
}
