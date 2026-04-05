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

package com.lu.shortlink.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import static com.lu.shortlink.admin.common.constant.RedisCacheConstant.USER_TOKEN_VERSION_KEY;

/**
 * 用户 token 版本号服务。
 */
@Service
@RequiredArgsConstructor
public class TokenVersionService {

    private final StringRedisTemplate stringRedisTemplate;

    public long getTokenVersion(String userId) {
        String value = stringRedisTemplate.opsForValue().get(String.format(USER_TOKEN_VERSION_KEY, userId));
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public long revokeAllTokens(String userId) {
        Long nextVersion = stringRedisTemplate.opsForValue().increment(String.format(USER_TOKEN_VERSION_KEY, userId));
        return nextVersion == null ? 0L : nextVersion;
    }
}

