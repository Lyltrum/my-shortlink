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

package com.lu.shortlink.admin.service;

import com.lu.shortlink.admin.config.JwtProperties;
import com.lu.shortlink.admin.util.JwtKeyGenerator;
import com.lu.shortlink.admin.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * JWT Token 服务，封装密钥加载与 Token 生成/验证逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtProperties jwtProperties;
    private final ResourceLoader resourceLoader;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            String privateKeyPath = jwtProperties.getPrivateKeyPath();
            String publicKeyPath = jwtProperties.getPublicKeyPath();
            this.privateKey = JwtKeyGenerator.loadPrivateKey(
                    resourceLoader.getResource(privateKeyPath).getInputStream()
            );
            this.publicKey = JwtKeyGenerator.loadPublicKey(
                    resourceLoader.getResource(publicKeyPath).getInputStream()
            );
            log.info("JWT RSA 密钥加载完毕，AccessToken TTL={}s, RefreshToken TTL={}s",
                    jwtProperties.getAccessTokenTtl(), jwtProperties.getRefreshTokenTtl());
        } catch (IOException e) {
            throw new RuntimeException("加载 JWT 密钥文件失败", e);
        }
    }

    public String generateAccessToken(String userId, String username) {
        return JwtUtil.generateAccessToken(privateKey, userId, username, jwtProperties.getAccessTokenTtl() * 1000L);
    }

    public String generateRefreshToken(String userId, String username) {
        return JwtUtil.generateRefreshToken(privateKey, userId, username, jwtProperties.getRefreshTokenTtl() * 1000L);
    }

    public Claims parseAccessToken(String token) {
        return JwtUtil.parseAccessToken(publicKey, token);
    }

    public Claims parseRefreshToken(String token) {
        return JwtUtil.parseRefreshToken(publicKey, token);
    }

    public boolean validateToken(String token) {
        return JwtUtil.validateToken(publicKey, token);
    }

    public long getAccessTokenTtl() {
        return jwtProperties.getAccessTokenTtl();
    }
}
