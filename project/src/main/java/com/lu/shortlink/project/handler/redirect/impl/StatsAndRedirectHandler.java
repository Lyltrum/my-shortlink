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
import com.lu.shortlink.project.handler.redirect.StatsSnapshotBuilder;
import com.lu.shortlink.project.mq.producer.ShortLinkStatsSaveProducer;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

/**
 * 责任链节点 8（终态）：统计快照入队 + sendRedirect
 *
 * <p>此节点为链的终止节点，Cookie 读写和快照构建委托给 {@link StatsSnapshotBuilder}，
 * 必须在 sendRedirect 之前完成（否则浏览器不会携带新写入的 Cookie）。
 * 执行完毕后不调用 chain.proceed()，链自然结束。
 */
@Component
@RequiredArgsConstructor
public class StatsAndRedirectHandler implements RedirectHandler {

    private final ShortLinkStatsSaveProducer shortLinkStatsSaveProducer;
    private final StatsSnapshotBuilder statsSnapshotBuilder;

    @SneakyThrows
    @Override
    public void handle(RedirectContext ctx, RedirectHandlerChain chain) {
        shortLinkStatsSaveProducer.send(statsSnapshotBuilder.build(ctx));
        ctx.getResponse().sendRedirect(ctx.getResolvedOriginUrl());
    }
}
