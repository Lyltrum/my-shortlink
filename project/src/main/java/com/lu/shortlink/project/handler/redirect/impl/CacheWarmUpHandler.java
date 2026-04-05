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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 责任链节点 7：缓存预热（DB 查询结果回写 L1 + L2）
 *
 * <p>在 DbLookupHandler 填充 ctx 之后、StatsAndRedirectHandler 跳转之前执行，
 * 使下一次相同短链接请求可直接命中 L1CacheHandler，无需再次查库。
 */
@Component
@RequiredArgsConstructor
public class CacheWarmUpHandler implements RedirectHandler {

    private final ShortLinkCacheManager shortLinkCacheManager;

    @Override
    public void handle(RedirectContext ctx, RedirectHandlerChain chain) {
        shortLinkCacheManager.put(
                ctx.getFullShortUrl(),
                ctx.getResolvedOriginUrl(),
                ctx.getResolvedValidDate()
        );
        chain.proceed(ctx);
    }
}
