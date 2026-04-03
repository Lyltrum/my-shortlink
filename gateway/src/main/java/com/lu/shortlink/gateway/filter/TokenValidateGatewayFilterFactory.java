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

package com.lu.shortlink.gateway.filter;

import com.alibaba.fastjson2.JSON;
import com.lu.shortlink.gateway.config.Config;
import com.lu.shortlink.gateway.config.JwtConfig;
import com.lu.shortlink.gateway.dto.GatewayErrorResult;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * SpringCloud Gateway JWT Token 验证过滤器
 */
@Slf4j
@Component
public class TokenValidateGatewayFilterFactory extends AbstractGatewayFilterFactory<Config> {

    private final JwtConfig jwtConfig;

    public TokenValidateGatewayFilterFactory(JwtConfig jwtConfig) {
        super(Config.class);
        this.jwtConfig = jwtConfig;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String requestPath = request.getPath().toString();
            // 如果在白名单中则跳过 JWT 校验
            if (isPathInWhiteList(requestPath, config.getWhitePathList())) {
                return chain.filter(exchange);
            }
            // 从 Authorization: Bearer <token> 头部获取 JWT
            String authHeader = request.getHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return writeError(exchange, "Missing or invalid Authorization header");
            }
            String token = authHeader.substring(7);
            try {
                // 验签并解析 Access Token
                Claims claims = jwtConfig.parseAccessToken(token);
                String userId = claims.getSubject();
                String username = claims.get("username", String.class);
                // 将用户信息注入下游 Header（与原有逻辑保持兼容）
                ServerHttpRequest mutatedRequest = request.mutate()
                        .headers(httpHeaders -> {
                            httpHeaders.set("userId", userId);
                            httpHeaders.set("username", URLEncoder.encode(username, StandardCharsets.UTF_8));
                        })
                        .build();
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            } catch (Exception e) {
                log.warn("JWT 验签失败 [{}]: {}", requestPath, e.getMessage());
                return writeError(exchange, "Invalid or expired token");
            }
        };
    }

    private boolean isPathInWhiteList(String requestPath, List<String> whitePathList) {
        return whitePathList != null
                && whitePathList.stream().anyMatch(requestPath::startsWith);
    }

    private Mono<Void> writeError(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        GatewayErrorResult resultMessage = GatewayErrorResult.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .message(message)
                .build();
        DataBufferFactory bufferFactory = response.bufferFactory();
        return response.writeWith(Mono.fromSupplier(() ->
                bufferFactory.wrap(JSON.toJSONString(resultMessage).getBytes(StandardCharsets.UTF_8))
        ));
    }
}
