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

import com.lu.shortlink.project.handler.redirect.RedirectContext;
import com.lu.shortlink.project.handler.redirect.RedirectHandler;
import com.lu.shortlink.project.handler.redirect.RedirectHandlerChain;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.redisson.api.RBloomFilter;
import org.springframework.stereotype.Component;

/**
 * 责任链节点 2：Bloom Filter 过滤（防缓存穿透）
 *
 * <p>不含时短链接一定不存在，直接 404，中止链。
 * 含（可能存在）时：调用 chain.proceed(ctx) 继续往下走。
 */
@Component
@RequiredArgsConstructor
public class BloomFilterHandler implements RedirectHandler {

    private final RBloomFilter<String> shortUriCreateCachePenetrationBloomFilter;

    @SneakyThrows
    @Override
    public void handle(RedirectContext ctx, RedirectHandlerChain chain) {
        if (!shortUriCreateCachePenetrationBloomFilter.contains(ctx.getFullShortUrl())) {
            ctx.getResponse().sendRedirect("/page/notfound");
            return;
        }
        chain.proceed(ctx);
    }
}
