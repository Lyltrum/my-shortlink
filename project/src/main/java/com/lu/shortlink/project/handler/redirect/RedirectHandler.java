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

package com.lu.shortlink.project.handler.redirect;

/**
 * 短链接跳转责任链节点接口（GoF Chain of Responsibility）
 *
 * <p>每个 Handler 独立决定：
 * <ol>
 *   <li>执行本节点逻辑；</li>
 *   <li>若需要继续则调用 {@code chain.proceed(ctx)}，将控制权交给下一个节点；</li>
 *   <li>若需要中止（如命中缓存/404）则直接返回，不调用 proceed。</li>
 * </ol>
 */
public interface RedirectHandler {

    /**
     * @param ctx   本次请求的上下文，携带输入数据和各节点产生的中间状态
     * @param chain 剩余链（调用 proceed 继续执行后续 Handler）
     */
    void handle(RedirectContext ctx, RedirectHandlerChain chain);
}
