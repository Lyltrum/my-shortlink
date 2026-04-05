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
import com.github.benmanes.caffeine.cache.Cache;
import com.lu.shortlink.project.cache.ParsedUA;
import com.lu.shortlink.project.dto.biz.RawStatsSnapshot;
import com.lu.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_TOPIC_KEY;
import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_UIP_KEY;
import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_UV_KEY;

/**
 * 短链接监控状态保存消息队列生产者（本地缓冲 + 批量 flush）
 *
 * <p>主线程仅将原始快照入队（O(1) 内存操作）；flush 线程通过 Redis Pipeline
 * 将整批 SADD 压缩为单次网络往返，再逐条写入 Redis Stream。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortLinkStatsSaveProducer {

    private final StringRedisTemplate stringRedisTemplate;
    private final Cache<String, ParsedUA> uaParsingCache;

    private final ConcurrentLinkedQueue<RawStatsSnapshot> statsBuffer = new ConcurrentLinkedQueue<>();

    /**
     * 统计原始快照入本地缓冲队列，由调度器批量 flush 到 Redis Stream
     */
    public void send(RawStatsSnapshot snapshot) {
        statsBuffer.offer(snapshot);
    }

    /**
     * 将缓冲队列中所有记录批量写入 Redis Stream。
     * <ol>
     *   <li>排空队列，得到当前批次</li>
     *   <li>Pipeline 批量执行所有 SADD（UV + UIP），单次网络往返</li>
     *   <li>解析 pipeline 结果，从 UA 缓存获取解析结果，构建完整 DTO</li>
     *   <li>逐条写入 Redis Stream；失败则重新入队原始快照</li>
     * </ol>
     */
    public void flush() {
        if (statsBuffer.isEmpty()) {
            return;
        }
        List<RawStatsSnapshot> batch = new ArrayList<>();
        RawStatsSnapshot item;
        while ((item = statsBuffer.poll()) != null) {
            batch.add(item);
        }

        // Pipeline: 2×N 次 SADD → 1 次网络往返
        List<Object> pipelineResults;
        try {
            pipelineResults = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                StringRedisConnection conn = (StringRedisConnection) connection;
                for (RawStatsSnapshot snapshot : batch) {
                    conn.sAdd(SHORT_LINK_STATS_UV_KEY + snapshot.getFullShortUrl(), snapshot.getUv());
                    conn.sAdd(SHORT_LINK_STATS_UIP_KEY + snapshot.getFullShortUrl(), snapshot.getRemoteAddr());
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Pipeline SADD 批量执行失败，重新入队 {} 条记录", batch.size(), e);
            batch.forEach(statsBuffer::offer);
            return;
        }

        for (int i = 0; i < batch.size(); i++) {
            RawStatsSnapshot snapshot = batch.get(i);
            try {
                // pipeline 结果顺序：[uv0, uip0, uv1, uip1, ...]
                boolean uvFirstFlag = Long.valueOf(1L).equals(pipelineResults.get(i * 2));
                boolean uipFirstFlag = Long.valueOf(1L).equals(pipelineResults.get(i * 2 + 1));

                // UA 解析走 Caffeine 缓存（500 条，命中率极高）
                String ua = snapshot.getUserAgent();
                ParsedUA parsedUA = uaParsingCache.get(ua != null ? ua : "", this::parseUA);

                ShortLinkStatsRecordDTO statsRecord = ShortLinkStatsRecordDTO.builder()
                        .fullShortUrl(snapshot.getFullShortUrl())
                        .uv(snapshot.getUv())
                        .uvFirstFlag(uvFirstFlag)
                        .uipFirstFlag(uipFirstFlag)
                        .remoteAddr(snapshot.getRemoteAddr())
                        .os(parsedUA.os())
                        .browser(parsedUA.browser())
                        .device(parsedUA.device())
                        .network(parseNetwork(snapshot.getRemoteAddr()))
                        .currentDate(snapshot.getCurrentDate())
                        .build();

                Map<String, String> producerMap = new HashMap<>();
                producerMap.put("statsRecord", JSON.toJSONString(statsRecord));
                stringRedisTemplate.opsForStream().add(SHORT_LINK_STATS_STREAM_TOPIC_KEY, producerMap);
            } catch (Exception e) {
                log.error("统计记录写入 Redis Stream 失败，重新入队。snapshot={}", snapshot, e);
                statsBuffer.offer(snapshot);
            }
        }
    }

    // -------- UA 解析私有方法（纯字符串操作，不依赖 HttpServletRequest）--------

    private ParsedUA parseUA(String ua) {
        String uaLower = (ua == null || ua.isEmpty()) ? "" : ua.toLowerCase();
        return new ParsedUA(parseOs(uaLower), parseBrowser(uaLower), parseDevice(uaLower), "");
    }

    private String parseOs(String uaLower) {
        if (uaLower.contains("windows")) return "Windows";
        if (uaLower.contains("android")) return "Android";
        if (uaLower.contains("iphone") || uaLower.contains("ipad")) return "iOS";
        if (uaLower.contains("mac")) return "Mac OS";
        if (uaLower.contains("linux")) return "Linux";
        return "Unknown";
    }

    private String parseBrowser(String uaLower) {
        if (uaLower.contains("edg")) return "Microsoft Edge";
        if (uaLower.contains("chrome")) return "Google Chrome";
        if (uaLower.contains("firefox")) return "Mozilla Firefox";
        if (uaLower.contains("safari")) return "Apple Safari";
        if (uaLower.contains("opera")) return "Opera";
        if (uaLower.contains("msie") || uaLower.contains("trident")) return "Internet Explorer";
        return "Unknown";
    }

    private String parseDevice(String uaLower) {
        return uaLower.contains("mobile") ? "Mobile" : "PC";
    }

    private String parseNetwork(String remoteAddr) {
        return (remoteAddr != null && (remoteAddr.startsWith("192.168.") || remoteAddr.startsWith("10.")))
                ? "WIFI" : "Mobile";
    }
}
