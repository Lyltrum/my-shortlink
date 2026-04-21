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

import com.alibaba.fastjson2.JSON;
import com.lu.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.lu.shortlink.project.mq.StatsMessageSender;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 实现：将统计 DTO 同步发送到 short-link-stats-topic。
 * short-link.stats.mq.type=rocketmq 时生效。
 */
@Component
@ConditionalOnProperty(name = "short-link.stats.mq.type", havingValue = "rocketmq")
@RequiredArgsConstructor
public class RocketMQStatsMessageSender implements StatsMessageSender {

    private final RocketMQTemplate rocketMQTemplate;

    @Override
    public void send(ShortLinkStatsRecordDTO statsRecord) {
        rocketMQTemplate.syncSend(
                "short-link-stats-topic",
                MessageBuilder.withPayload(JSON.toJSONString(statsRecord)).build()
        );
    }
}
