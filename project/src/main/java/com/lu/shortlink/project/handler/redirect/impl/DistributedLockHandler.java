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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import static com.lu.shortlink.project.common.constant.RedisKeyConstant.LOCK_GOTO_SHORT_LINK_KEY;

/**
 * 责任链节点 4：Redisson 分布式锁
 *
 * <p>加锁后调用 chain.proceed(ctx)，后续节点（DoubleCheck、DbLookup、CacheWarmUp、StatsAndRedirect）
 * 均在锁保护范围内完整执行，finally 中无条件解锁，与原始 try/finally 作用域完全等价。
 *
 * <p>本 Handler 自身无状态：锁对象在方法栈上创建，每次请求独立，不存在共享。
 */
@Component
@RequiredArgsConstructor
public class DistributedLockHandler implements RedirectHandler {

    private final RedissonClient redissonClient;

    @Override
    public void handle(RedirectContext ctx, RedirectHandlerChain chain) {
        RLock lock = redissonClient.getLock(String.format(LOCK_GOTO_SHORT_LINK_KEY, ctx.getFullShortUrl()));
        lock.lock();
        try {
            chain.proceed(ctx);
        } finally {
            lock.unlock();
        }
    }
}
