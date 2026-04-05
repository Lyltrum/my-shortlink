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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * 短链接跳转责任链上下文
 *
 * <p>生命周期：每次 restoreUrl() 请求创建一个新实例，Handler 之间通过此对象传递中间状态。
 * <ul>
 *   <li>输入字段：fullShortUrl、request、response——在链启动前填充，Handler 中只读</li>
 *   <li>中间字段：resolvedGid、resolvedOriginUrl、resolvedValidDate
 *       ——由 DbLookupHandler 填充，供 CacheWarmUpHandler 和 StatsAndRedirectHandler 读取</li>
 * </ul>
 */
@Data
@Builder
public class RedirectContext {

    // ---------- 输入（构造时填充，不可变语义）----------

    /** host + port + "/" + shortUri，如 "nurl.ink/abc123" */
    private String fullShortUrl;

    private HttpServletRequest request;

    private HttpServletResponse response;

    // ---------- DbLookupHandler 填充的中间状态 ----------

    /** 路由表中查到的 gid */
    private String resolvedGid;

    /** 主表中查到的原始长链接 */
    private String resolvedOriginUrl;

    /** 主表中查到的业务过期时间，null 表示永久有效 */
    private Date resolvedValidDate;
}
