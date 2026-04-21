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

package com.lu.shortlink.project.mq;

import com.lu.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;

/**
 * 统计消息发送接口，屏蔽底层 MQ 实现（Redis Stream / RocketMQ）。
 * 通过 short-link.stats.mq.type 配置项选择具体实现。
 */
public interface StatsMessageSender {

    void send(ShortLinkStatsRecordDTO statsRecord);
}
