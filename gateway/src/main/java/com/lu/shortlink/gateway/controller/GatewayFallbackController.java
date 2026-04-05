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

package com.lu.shortlink.gateway.controller;

import com.lu.shortlink.gateway.dto.GatewayErrorResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网关熔断后的统一降级响应。
 */
@RestController
public class GatewayFallbackController {

    @RequestMapping("/fallback/admin")
    public ResponseEntity<GatewayErrorResult> adminFallback() {
        return buildResponse("管理服务暂时不可用，请稍后重试");
    }

    @RequestMapping("/fallback/project")
    public ResponseEntity<GatewayErrorResult> projectFallback() {
        return buildResponse("短链接服务暂时不可用，请稍后重试");
    }

    private ResponseEntity<GatewayErrorResult> buildResponse(String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(GatewayErrorResult.builder()
                        .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                        .message(message)
                        .build());
    }
}
