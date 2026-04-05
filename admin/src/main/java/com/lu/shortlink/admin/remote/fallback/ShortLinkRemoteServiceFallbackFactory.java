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

package com.lu.shortlink.admin.remote.fallback;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lu.shortlink.admin.common.convention.errorcode.BaseErrorCode;
import com.lu.shortlink.admin.common.convention.result.Result;
import com.lu.shortlink.admin.dto.req.RecycleBinRecoverReqDTO;
import com.lu.shortlink.admin.dto.req.RecycleBinRemoveReqDTO;
import com.lu.shortlink.admin.dto.req.RecycleBinSaveReqDTO;
import com.lu.shortlink.admin.remote.ShortLinkActualRemoteService;
import com.lu.shortlink.admin.remote.dto.req.ShortLinkCreateReqDTO;
import com.lu.shortlink.admin.remote.dto.req.ShortLinkUpdateReqDTO;
import com.lu.shortlink.admin.remote.dto.resp.ShortLinkCreateRespDTO;
import com.lu.shortlink.admin.remote.dto.resp.ShortLinkGroupCountQueryRespDTO;
import com.lu.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import com.lu.shortlink.admin.remote.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import com.lu.shortlink.admin.remote.dto.resp.ShortLinkStatsRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fallback factory for project service.
 */
@Slf4j
@Component
public class ShortLinkRemoteServiceFallbackFactory implements FallbackFactory<ShortLinkActualRemoteService> {

    static final String PROJECT_UNAVAILABLE_MESSAGE = "Short link service is temporarily unavailable, please try again later";

    @Override
    public ShortLinkActualRemoteService create(Throwable cause) {
        log.warn("short-link-project fallback triggered: {}", cause == null ? "unknown" : cause.getMessage(), cause);
        return new ShortLinkActualRemoteService() {
            @Override
            public Result<ShortLinkCreateRespDTO> createShortLink(ShortLinkCreateReqDTO requestParam) {
                return failure();
            }

            @Override
            public Result<Void> updateShortLink(ShortLinkUpdateReqDTO requestParam) {
                return failure();
            }

            @Override
            public Result<Page<ShortLinkPageRespDTO>> pageShortLink(String gid, String orderTag, Long current, Long size) {
                return failure();
            }

            @Override
            public Result<List<ShortLinkGroupCountQueryRespDTO>> listGroupShortLinkCount(List<String> requestParam) {
                return failure();
            }

            @Override
            public Result<String> getTitleByUrl(String url) {
                return failure();
            }

            @Override
            public Result<Void> saveRecycleBin(RecycleBinSaveReqDTO requestParam) {
                return failure();
            }

            @Override
            public Result<Page<ShortLinkPageRespDTO>> pageRecycleBinShortLink(List<String> gidList, Long current, Long size) {
                return failure();
            }

            @Override
            public Result<Void> recoverRecycleBin(RecycleBinRecoverReqDTO requestParam) {
                return failure();
            }

            @Override
            public Result<Void> removeRecycleBin(RecycleBinRemoveReqDTO requestParam) {
                return failure();
            }

            @Override
            public Result<ShortLinkStatsRespDTO> oneShortLinkStats(String fullShortUrl, String gid, Integer enableStatus, String startDate, String endDate) {
                return failure();
            }

            @Override
            public Result<ShortLinkStatsRespDTO> groupShortLinkStats(String gid, String startDate, String endDate) {
                return failure();
            }

            @Override
            public Result<Page<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(String fullShortUrl, String gid, String startDate, String endDate, Integer enableStatus, Long current, Long size) {
                return failure();
            }

            @Override
            public Result<Page<ShortLinkStatsAccessRecordRespDTO>> groupShortLinkStatsAccessRecord(String gid, String startDate, String endDate, Long current, Long size) {
                return failure();
            }
        };
    }

    private <T> Result<T> failure() {
        Result<T> result = new Result<>();
        result.setCode(BaseErrorCode.REMOTE_ERROR.code());
        result.setMessage(PROJECT_UNAVAILABLE_MESSAGE);
        return result;
    }
}

