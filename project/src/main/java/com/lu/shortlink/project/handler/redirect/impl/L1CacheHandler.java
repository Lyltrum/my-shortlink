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

import com.lu.shortlink.project.cache.ShortLinkCacheManager;
import com.lu.shortlink.project.handler.redirect.RedirectContext;
import com.lu.shortlink.project.handler.redirect.RedirectHandler;
import com.lu.shortlink.project.handler.redirect.RedirectHandlerChain;
import com.lu.shortlink.project.handler.redirect.StatsSnapshotBuilder;
import com.lu.shortlink.project.mq.producer.ShortLinkStatsSaveProducer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

/**
 * 责任链节点 1：Caffeine L1 + Redis L2 多级缓存命中检查
 *
 * <p>命中时：构建统计快照入队 + sendRedirect，中止链。
 * 未命中时：调用 chain.proceed(ctx) 继续往下走。
 */
@Component
@RequiredArgsConstructor
public class L1CacheHandler implements RedirectHandler {

    private final ShortLinkCacheManager shortLinkCacheManager;
    private final ShortLinkStatsSaveProducer shortLinkStatsSaveProducer;
    private final StatsSnapshotBuilder statsSnapshotBuilder;

    @SneakyThrows
    @Override
    public void handle(RedirectContext ctx, RedirectHandlerChain chain) {
        String cachedUrl = shortLinkCacheManager.get(ctx.getFullShortUrl());
        if (cachedUrl != null) {
            shortLinkStatsSaveProducer.send(statsSnapshotBuilder.build(ctx));
            ctx.getResponse().sendRedirect(cachedUrl);
            return;
        }
        chain.proceed(ctx);
    }
}
