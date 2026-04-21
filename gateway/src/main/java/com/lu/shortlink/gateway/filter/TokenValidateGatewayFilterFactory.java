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
import org.springframework.data.redis.core.StringRedisTemplate;
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
 * SpringCloud Gateway JWT Token 校验过滤器
 */
@Slf4j
@Component
public class TokenValidateGatewayFilterFactory extends AbstractGatewayFilterFactory<Config> {

    private static final String USER_TOKEN_VERSION_KEY = "short-link:token-version:%s";

    private final JwtConfig jwtConfig;
    private final StringRedisTemplate stringRedisTemplate;

    public TokenValidateGatewayFilterFactory(JwtConfig jwtConfig, StringRedisTemplate stringRedisTemplate) {
        super(Config.class);
        this.jwtConfig = jwtConfig;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String requestPath = request.getPath().toString();
            if (isPathInWhiteList(requestPath, config.getWhitePathList())) {
                return chain.filter(exchange);
            }
            String authHeader = request.getHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return writeError(exchange, "Missing or invalid Authorization header");
            }
            String token = authHeader.substring(7);

            try {
                Claims claims = jwtConfig.parseAccessToken(token);
                String userId = claims.getSubject();
                String username = claims.get("username", String.class);
                Number tokenVersionInToken = claims.get("tokenVersion", Number.class);
                long tokenVersionInStore = getTokenVersion(userId);
                if (tokenVersionInToken == null || tokenVersionInToken.longValue() != tokenVersionInStore) {
                    return writeError(exchange, "Token has been revoked");
                }
                ServerHttpRequest mutatedRequest = request.mutate()  
                        .headers(httpHeaders -> {
                            httpHeaders.set("userId", userId);
                            httpHeaders.set("username", URLEncoder.encode(username, StandardCharsets.UTF_8));
                        })
                        .build();
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            } catch (Exception e) {
                log.warn("JWT verify failed [{}]: {}", requestPath, e.getMessage());
                return writeError(exchange, "Invalid or expired token");
            }
        };
    }

    private long getTokenVersion(String userId) {
        String value = stringRedisTemplate.opsForValue().get(String.format(USER_TOKEN_VERSION_KEY, userId));
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
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
