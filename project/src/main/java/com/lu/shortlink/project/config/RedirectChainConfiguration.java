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

import com.lu.shortlink.project.handler.redirect.RedirectHandler;
import com.lu.shortlink.project.handler.redirect.RedirectHandlerChain;
import com.lu.shortlink.project.handler.redirect.impl.BloomFilterHandler;
import com.lu.shortlink.project.handler.redirect.impl.CacheWarmUpHandler;
import com.lu.shortlink.project.handler.redirect.impl.DbLookupHandler;
import com.lu.shortlink.project.handler.redirect.impl.DistributedLockHandler;
import com.lu.shortlink.project.handler.redirect.impl.DoubleCheckHandler;
import com.lu.shortlink.project.handler.redirect.impl.L1CacheHandler;
import com.lu.shortlink.project.handler.redirect.impl.NullCacheHandler;
import com.lu.shortlink.project.handler.redirect.impl.StatsAndRedirectHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 短链接跳转责任链装配配置
 *
 * <p>使用 lambda 右折叠（right-fold）方式构建不可变链，线程安全原理：
 * <pre>
 * handlers = [L1Cache, BloomFilter, NullCache, DistributedLock,
 *             DoubleCheck, DbLookup, CacheWarmUp, StatsAndRedirect]
 *
 * fold 过程（从右向左）：
 *   terminal = ctx -> {}                                      // 空终止节点
 *   chain_7  = ctx -> StatsAndRedirect.handle(ctx, terminal)
 *   chain_6  = ctx -> CacheWarmUp.handle(ctx, chain_7)
 *   chain_5  = ctx -> DbLookup.handle(ctx, chain_6)
 *   chain_4  = ctx -> DoubleCheck.handle(ctx, chain_5)
 *   chain_3  = ctx -> DistributedLock.handle(ctx, chain_4)
 *   chain_2  = ctx -> NullCache.handle(ctx, chain_3)
 *   chain_1  = ctx -> BloomFilter.handle(ctx, chain_2)
 *   chain_0  = ctx -> L1Cache.handle(ctx, chain_1)           // 最终 Bean（入口）
 * </pre>
 *
 * <p>每个 lambda 闭包只捕获不可变引用（Handler 单例 + 下一个 Chain 引用），
 * RedirectContext 由调用方每次 new，无共享可变状态，高并发下完全线程安全。
 */
@Configuration
public class RedirectChainConfiguration {

    @Bean
    public RedirectHandlerChain redirectHandlerChain(
            L1CacheHandler l1CacheHandler,
            BloomFilterHandler bloomFilterHandler,
            NullCacheHandler nullCacheHandler,
            DistributedLockHandler distributedLockHandler,
            DoubleCheckHandler doubleCheckHandler,
            DbLookupHandler dbLookupHandler,
            CacheWarmUpHandler cacheWarmUpHandler,
            StatsAndRedirectHandler statsAndRedirectHandler) {

        List<RedirectHandler> handlers = List.of(
                l1CacheHandler,
                bloomFilterHandler,
                nullCacheHandler,
                distributedLockHandler,
                doubleCheckHandler,
                dbLookupHandler,
                cacheWarmUpHandler,
                statsAndRedirectHandler
        );

        // 右折叠：从最后一个 Handler 开始，逐步向前包装
        RedirectHandlerChain chain = ctx -> { /* 终止节点，无操作 */ };
        for (int i = handlers.size() - 1; i >= 0; i--) {
            final RedirectHandler handler = handlers.get(i);
            final RedirectHandlerChain next = chain;
            chain = ctx -> handler.handle(ctx, next);
        }
        return chain;
    }
}
