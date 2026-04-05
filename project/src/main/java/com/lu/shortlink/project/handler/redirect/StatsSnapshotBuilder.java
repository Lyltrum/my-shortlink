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

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.lu.shortlink.project.dto.biz.RawStatsSnapshot;
import com.lu.shortlink.project.toolkit.LinkUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 跳转统计原始快照构建器
 *
 * <p>完整保留 Cookie 读写逻辑：
 * <ul>
 *   <li>若请求中已有名为 "uv" 的 Cookie，直接读取其值；</li>
 *   <li>否则生成新 UUID 写入响应 Cookie（30 天，path = shortUri 部分）。</li>
 * </ul>
 * 此操作必须在 sendRedirect 之前完成，否则浏览器不会携带新 Cookie。
 *
 * <p>供 L1CacheHandler、DoubleCheckHandler、StatsAndRedirectHandler 共用注入。
 */
@Component
public class StatsSnapshotBuilder {

    public RawStatsSnapshot build(RedirectContext ctx) {
        String fullShortUrl = ctx.getFullShortUrl();
        HttpServletRequest request = ctx.getRequest();
        HttpServletResponse response = ctx.getResponse();

        Cookie[] cookies = request.getCookies();
        AtomicReference<String> uv = new AtomicReference<>();
        Runnable addResponseCookieTask = () -> {
            uv.set(UUID.fastUUID().toString());
            Cookie uvCookie = new Cookie("uv", uv.get());
            uvCookie.setMaxAge(60 * 60 * 24 * 30);
            uvCookie.setPath(StrUtil.sub(fullShortUrl, fullShortUrl.indexOf("/"), fullShortUrl.length()));
            response.addCookie(uvCookie);
        };
        if (ArrayUtil.isNotEmpty(cookies)) {
            Arrays.stream(cookies)
                    .filter(each -> Objects.equals(each.getName(), "uv"))
                    .findFirst()
                    .map(Cookie::getValue)
                    .ifPresentOrElse(uv::set, addResponseCookieTask);
        } else {
            addResponseCookieTask.run();
        }
        return RawStatsSnapshot.builder()
                .fullShortUrl(fullShortUrl)
                .uv(uv.get())
                .remoteAddr(LinkUtil.getActualIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .currentDate(new Date())
                .build();
    }
}
