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

/**
 * L1 本地缓存条目：originUrl + 业务过期时间戳（ms，0 表示永久）
 */
public record ShortLinkCacheEntry(String originUrl, long expireAtMs) {

    /** 永久有效链接使用此工厂方法 */
    public static ShortLinkCacheEntry permanent(String originUrl) {
        return new ShortLinkCacheEntry(originUrl, 0L);
    }

    /** 有过期时间的链接 */
    public static ShortLinkCacheEntry withExpiry(String originUrl, long expireAtMs) {
        return new ShortLinkCacheEntry(originUrl, expireAtMs);
    }

    /** 业务层是否已过期（Caffeine 到期前的二次兜底校验） */
    public boolean isBusinessExpired() {
        return expireAtMs > 0 && System.currentTimeMillis() > expireAtMs;
    }
}
