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

package com.lu.shortlink.project.handler.redirect.impl;

import cn.hutool.core.util.StrUtil;
import com.lu.shortlink.project.handler.redirect.RedirectContext;
import com.lu.shortlink.project.handler.redirect.RedirectHandler;
import com.lu.shortlink.project.handler.redirect.RedirectHandlerChain;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import static com.lu.shortlink.project.common.constant.RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY;

/**
 * 责任链节点 3：Redis 空值缓存检查（防缓存穿透兜底）
 *
 * <p>命中空值（Bloom Filter 误判后已写入 "-"）时：直接 404，中止链。
 * 不存在时：调用 chain.proceed(ctx) 继续往下走。
 */
@Component
@RequiredArgsConstructor
public class NullCacheHandler implements RedirectHandler {

    private final StringRedisTemplate stringRedisTemplate;

    @SneakyThrows
    @Override
    public void handle(RedirectContext ctx, RedirectHandlerChain chain) {
        String nullValue = stringRedisTemplate.opsForValue()
                .get(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, ctx.getFullShortUrl()));
        if (StrUtil.isNotBlank(nullValue)) {
            ctx.getResponse().sendRedirect("/page/notfound");
            return;
        }
        chain.proceed(ctx);
    }
}
