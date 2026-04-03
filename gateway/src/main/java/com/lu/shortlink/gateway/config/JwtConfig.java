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

package com.lu.shortlink.gateway.config;

import com.lu.shortlink.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.security.PublicKey;

/**
 * JWT 配置类，负责在启动时加载公钥
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class JwtConfig {

    private final JwtProperties jwtProperties;

    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        String publicKeyPath = jwtProperties.getPublicKeyPath();
        this.publicKey = JwtUtil.loadPublicKey(publicKeyPath);
        log.info("Gateway JWT 公钥加载完毕，路径: {}", publicKeyPath);
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public Claims parseAccessToken(String token) {
        return JwtUtil.parseAccessToken(publicKey, token);
    }

    public boolean validateToken(String token) {
        return JwtUtil.validateToken(publicKey, token);
    }
}
