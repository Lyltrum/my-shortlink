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

package com.lu.shortlink.project.cache;

import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.lu.shortlink.project.toolkit.LinkUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.lu.shortlink.project.common.constant.RedisKeyConstant.GOTO_SHORT_LINK_KEY;

/**
 * 短链接多级缓存管理器（L1: Caffeine，L2: Redis）
 *
 * <pre>
 * 读取顺序：L1 → L2 → null（未命中由调用方决定是否回源 DB）
 * 写入：同时写 L1（逐条 TTL = min(1min, 剩余有效期)）和 L2（业务 TTL）
 * 失效：同时删 L1 和 L2
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class ShortLinkCacheManager {

    @Value("${short-link.cache.l1-enabled:true}")
    private boolean l1Enabled;

    private final Cache<String, ShortLinkCacheEntry> shortLinkLocalCache;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 从 L1 → L2 依次查找 originUrl。
     * L2 命中时自动回填 L1（不带 validDate，使用剩余 Redis TTL 估算无意义，故用永久条目默认 1min 过期）。
     *
     * @return originUrl，两级均未命中返回 null
     */
    public String get(String fullShortUrl) {
        if (l1Enabled) {
            ShortLinkCacheEntry local = shortLinkLocalCache.getIfPresent(fullShortUrl);
            if (local != null) {
                if (local.isBusinessExpired()) {
                    shortLinkLocalCache.invalidate(fullShortUrl);
                    return null;
                }
                return local.originUrl();
            }
        }
        String redisUrl = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl));
        if (l1Enabled && StrUtil.isNotBlank(redisUrl)) {
            // 回填 L1：此处无法获取原始 validDate，使用永久条目（Caffeine 默认 1min TTL）
            shortLinkLocalCache.put(fullShortUrl, ShortLinkCacheEntry.permanent(redisUrl));
        }
        return StrUtil.isNotBlank(redisUrl) ? redisUrl : null;
    }

    /**
     * 将短链接写入 L1 + L2。
     * L1 TTL = min(1min, 剩余有效期)；L2 TTL = getLinkCacheValidTime(validDate)。
     *
     * @param validDate 短链接业务过期时间，null 表示永久
     */
    public void put(String fullShortUrl, String originUrl, Date validDate) {
        // 写 L2
        stringRedisTemplate.opsForValue().set(
                String.format(GOTO_SHORT_LINK_KEY, fullShortUrl),
                originUrl,
                LinkUtil.getLinkCacheValidTime(validDate), TimeUnit.MILLISECONDS
        );
        // 写 L1
        if (l1Enabled) {
            putLocal(fullShortUrl, originUrl, validDate);
        }
    }

    /**
     * 仅更新 L1（L2 已有值，不需要重复写入时使用）。
     */
    public void putLocalOnly(String fullShortUrl, String originUrl, Date validDate) {
        if (l1Enabled) {
            putLocal(fullShortUrl, originUrl, validDate);
        }
    }

    /**
     * 同时失效 L1 和 L2。
     */
    public void invalidate(String fullShortUrl) {
        shortLinkLocalCache.invalidate(fullShortUrl);
        stringRedisTemplate.delete(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl));
    }

    // -------------------------------------------------------------------------

    private void putLocal(String fullShortUrl, String originUrl, Date validDate) {
        ShortLinkCacheEntry entry = validDate == null
                ? ShortLinkCacheEntry.permanent(originUrl)
                : ShortLinkCacheEntry.withExpiry(originUrl, validDate.getTime());
        shortLinkLocalCache.put(fullShortUrl, entry);
    }
}
