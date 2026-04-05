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
import com.lu.shortlink.project.cache.ShortLinkCacheManager;
import com.lu.shortlink.project.handler.redirect.RedirectContext;
import com.lu.shortlink.project.handler.redirect.RedirectHandler;
import com.lu.shortlink.project.handler.redirect.RedirectHandlerChain;
import com.lu.shortlink.project.handler.redirect.StatsSnapshotBuilder;
import com.lu.shortlink.project.mq.producer.ShortLinkStatsSaveProducer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import static com.lu.shortlink.project.common.constant.RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY;

/**
 * 责任链节点 5：锁内 Double-Check（L1/L2 缓存 + 空值缓存）
 *
 * <p>获取分布式锁后再次检查两类缓存，避免等锁期间其他线程已完成 DB 回源并写入缓存，
 * 防止重复查库。
 */
@Component
@RequiredArgsConstructor
public class DoubleCheckHandler implements RedirectHandler {

    private final ShortLinkCacheManager shortLinkCacheManager;
    private final StringRedisTemplate stringRedisTemplate;
    private final ShortLinkStatsSaveProducer shortLinkStatsSaveProducer;
    private final StatsSnapshotBuilder statsSnapshotBuilder;

    @SneakyThrows
    @Override
    public void handle(RedirectContext ctx, RedirectHandlerChain chain) {
        // double-check L1/L2 缓存
        String doubleCheckUrl = shortLinkCacheManager.get(ctx.getFullShortUrl());
        if (doubleCheckUrl != null) {
            shortLinkStatsSaveProducer.send(statsSnapshotBuilder.build(ctx));
            ctx.getResponse().sendRedirect(doubleCheckUrl);
            return;
        }
        // double-check 空值缓存
        String nullValue = stringRedisTemplate.opsForValue()
                .get(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, ctx.getFullShortUrl()));
        if (StrUtil.isNotBlank(nullValue)) {
            ctx.getResponse().sendRedirect("/page/notfound");
            return;
        }
        chain.proceed(ctx);
    }
}
