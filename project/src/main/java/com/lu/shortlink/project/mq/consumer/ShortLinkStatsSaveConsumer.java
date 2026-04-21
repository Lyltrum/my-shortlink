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

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.Week;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lu.shortlink.project.common.convention.exception.ServiceException;
import com.lu.shortlink.project.dao.entity.LinkAccessLogsDO;
import com.lu.shortlink.project.dao.entity.LinkAccessStatsDO;
import com.lu.shortlink.project.dao.entity.LinkBrowserStatsDO;
import com.lu.shortlink.project.dao.entity.LinkDeviceStatsDO;
import com.lu.shortlink.project.dao.entity.LinkLocaleStatsDO;
import com.lu.shortlink.project.dao.entity.LinkNetworkStatsDO;
import com.lu.shortlink.project.dao.entity.LinkOsStatsDO;
import com.lu.shortlink.project.dao.entity.LinkStatsTodayDO;
import com.lu.shortlink.project.dao.entity.ShortLinkGotoDO;
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
import com.lu.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.lu.shortlink.project.mq.idempotent.MessageQueueIdempotentHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.lu.shortlink.project.common.constant.RedisKeyConstant.LOCK_GID_UPDATE_KEY;
import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_DEAD_LETTER_TOPIC_KEY;
import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_GROUP_KEY;
import static com.lu.shortlink.project.common.constant.RedisKeyConstant.SHORT_LINK_STATS_STREAM_RETRY_COUNT_KEY;
import static com.lu.shortlink.project.common.constant.ShortLinkConstant.AMAP_REMOTE_URL;

/**
 * 鐭摼鎺ョ洃鎺х姸鎬佷繚瀛樻秷鎭槦鍒楁秷璐硅€?
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortLinkStatsSaveConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final RedissonClient redissonClient;
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;
    private final LinkStatsTodayMapper linkStatsTodayMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final MessageQueueIdempotentHandler messageQueueIdempotentHandler;

    @Value("${short-link.stats.locale.amap-key}")
    private String statsLocaleAmapKey;

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
            //解析消息体
            Map<String, String> producerMap = message.getValue();
            ShortLinkStatsRecordDTO statsRecord = JSON.parseObject(producerMap.get("statsRecord"), ShortLinkStatsRecordDTO.class);
            //保存统计数据
            actualSaveShortLinkStats(statsRecord);
            //ack+delet
            stringRedisTemplate.opsForStream().acknowledge(Objects.requireNonNull(stream), SHORT_LINK_STATS_STREAM_GROUP_KEY, id);
            stringRedisTemplate.opsForStream().delete(stream, id);
            //清除重试次数
            clearRetryState(id.toString());
            //设置消息处理完成
            messageQueueIdempotentHandler.setAccomplish(id.toString());
        } catch (Throwable ex) {
            //删除消息处理完成
            messageQueueIdempotentHandler.delMessageProcessed(id.toString());
            //如果达到重试次数，则将消息移动到死信队列
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

    public void actualSaveShortLinkStats(ShortLinkStatsRecordDTO statsRecord) {
        String fullShortUrl = statsRecord.getFullShortUrl();
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(String.format(LOCK_GID_UPDATE_KEY, fullShortUrl));
        RLock rLock = readWriteLock.readLock();
        rLock.lock();
        try {
            LambdaQueryWrapper<ShortLinkGotoDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                    .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
            ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(queryWrapper);
            String gid = shortLinkGotoDO.getGid();
            Date currentDate = statsRecord.getCurrentDate();
            int hour = DateUtil.hour(currentDate, true);
            Week week = DateUtil.dayOfWeekEnum(currentDate);
            int weekValue = week.getIso8601Value();
            LinkAccessStatsDO linkAccessStatsDO = LinkAccessStatsDO.builder()
                    .pv(1)
                    .uv(statsRecord.getUvFirstFlag() ? 1 : 0)
                    .uip(statsRecord.getUipFirstFlag() ? 1 : 0)
                    .hour(hour)
                    .weekday(weekValue)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkAccessStatsMapper.shortLinkStats(linkAccessStatsDO);
            //璋冪敤楂樺痉鍦板浘鎺ュ彛锛屾牴鎹甶p瑙ｆ瀽鍦板尯
            Map<String, Object> localeParamMap = new HashMap<>();
            localeParamMap.put("key", statsLocaleAmapKey);
            localeParamMap.put("ip", statsRecord.getRemoteAddr());
            String localeResultStr = HttpUtil.get(AMAP_REMOTE_URL, localeParamMap);
            JSONObject localeResultObj = JSON.parseObject(localeResultStr);
            String infoCode = localeResultObj.getString("infocode");
            String actualProvince = "鏈煡";
            String actualCity = "鏈煡";
            if (StrUtil.isNotBlank(infoCode) && StrUtil.equals(infoCode, "10000")) {
                String province = localeResultObj.getString("province");
                boolean unknownFlag = StrUtil.equals(province, "[]");
                LinkLocaleStatsDO linkLocaleStatsDO = LinkLocaleStatsDO.builder()
                        .province(actualProvince = unknownFlag ? actualProvince : province)
                        .city(actualCity = unknownFlag ? actualCity : localeResultObj.getString("city"))
                        .adcode(unknownFlag ? "鏈煡" : localeResultObj.getString("adcode"))
                        .cnt(1)
                        .fullShortUrl(fullShortUrl)
                        .country("涓浗")
                        .date(currentDate)
                        .build();
                linkLocaleStatsMapper.shortLinkLocaleState(linkLocaleStatsDO);
            }
            LinkOsStatsDO linkOsStatsDO = LinkOsStatsDO.builder()
                    .os(statsRecord.getOs())
                    .cnt(1)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkOsStatsMapper.shortLinkOsState(linkOsStatsDO);
            LinkBrowserStatsDO linkBrowserStatsDO = LinkBrowserStatsDO.builder()
                    .browser(statsRecord.getBrowser())
                    .cnt(1)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkBrowserStatsMapper.shortLinkBrowserState(linkBrowserStatsDO);
            LinkDeviceStatsDO linkDeviceStatsDO = LinkDeviceStatsDO.builder()
                    .device(statsRecord.getDevice())
                    .cnt(1)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkDeviceStatsMapper.shortLinkDeviceState(linkDeviceStatsDO);
            LinkNetworkStatsDO linkNetworkStatsDO = LinkNetworkStatsDO.builder()
                    .network(statsRecord.getNetwork())
                    .cnt(1)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkNetworkStatsMapper.shortLinkNetworkState(linkNetworkStatsDO);
            LinkAccessLogsDO linkAccessLogsDO = LinkAccessLogsDO.builder()
                    .user(statsRecord.getUv())
                    .ip(statsRecord.getRemoteAddr())
                    .browser(statsRecord.getBrowser())
                    .os(statsRecord.getOs())
                    .network(statsRecord.getNetwork())
                    .device(statsRecord.getDevice())
                    .locale(StrUtil.join("-", "涓浗", actualProvince, actualCity))
                    .fullShortUrl(fullShortUrl)
                    .build();
            linkAccessLogsMapper.insert(linkAccessLogsDO);
            shortLinkMapper.incrementStats(gid, fullShortUrl, 1, statsRecord.getUvFirstFlag() ? 1 : 0, statsRecord.getUipFirstFlag() ? 1 : 0);
            LinkStatsTodayDO linkStatsTodayDO = LinkStatsTodayDO.builder()
                    .todayPv(1)
                    .todayUv(statsRecord.getUvFirstFlag() ? 1 : 0)
                    .todayUip(statsRecord.getUipFirstFlag() ? 1 : 0)
                    .fullShortUrl(fullShortUrl)
                    .date(currentDate)
                    .build();
            linkStatsTodayMapper.shortLinkTodayState(linkStatsTodayDO);
        } finally {
            rLock.unlock();
        }
    }
}

