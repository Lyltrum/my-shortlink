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

package com.lu.shortlink.project.dto.biz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 短链接跳转原始快照：主线程仅提取原始值入队，耗时的 Redis SADD 和 UA 解析移至 flush 线程批量处理
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawStatsSnapshot {

    /** 完整短链接 */
    private String fullShortUrl;

    /** Cookie 中的 UV 值（已生成或已读取，无网络开销） */
    private String uv;

    /** 真实 IP（仅读取请求头，无网络开销） */
    private String remoteAddr;

    /** 原始 User-Agent 字符串（不解析） */
    private String userAgent;

    /** 当前时间 */
    private Date currentDate;
}
