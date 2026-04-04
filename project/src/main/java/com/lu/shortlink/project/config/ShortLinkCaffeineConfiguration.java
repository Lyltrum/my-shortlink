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

package com.lu.shortlink.project.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.lu.shortlink.project.cache.ShortLinkCacheEntry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 短链接本地缓存（L1）配置
 * 使用逐条 TTL（expireAfter），确保有过期时间的短链接不会在 L1 中超期驻留
 */
@Configuration
public class ShortLinkCaffeineConfiguration {

    private static final long MAX_TTL_NANOS = TimeUnit.MINUTES.toNanos(1);

    @Bean
    public Cache<String, ShortLinkCacheEntry> shortLinkLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfter(new Expiry<String, ShortLinkCacheEntry>() {
                    @Override
                    public long expireAfterCreate(String key, ShortLinkCacheEntry value, long currentTime) {
                        return ttlNanos(value);
                    }

                    @Override
                    public long expireAfterUpdate(String key, ShortLinkCacheEntry value, long currentTime, long currentDuration) {
                        return ttlNanos(value);
                    }

                    @Override
                    public long expireAfterRead(String key, ShortLinkCacheEntry value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }

                    private long ttlNanos(ShortLinkCacheEntry entry) {
                        if (entry.expireAtMs() <= 0) {
                            return MAX_TTL_NANOS;
                        }
                        long remainingMs = entry.expireAtMs() - System.currentTimeMillis();
                        if (remainingMs <= 0) return 0L;
                        return Math.min(MAX_TTL_NANOS, TimeUnit.MILLISECONDS.toNanos(remainingMs));
                    }
                })
                .build();
    }
}
