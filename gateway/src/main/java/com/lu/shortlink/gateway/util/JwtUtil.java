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

package com.lu.shortlink.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gateway JWT 验签工具类（仅验签，无需私钥）
 */
public class JwtUtil {

    private JwtUtil() {
    }

    /**
     * 从 PEM 文件加载公钥
     */
    public static PublicKey loadPublicKey(String publicKeyPath) {
        try {
            String content = Files.readString(Path.of(publicKeyPath.replace("file:", "")))
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            X509EncodedKeySpec spec = new X509EncodedKeySpec(Base64.getDecoder().decode(content));
            return java.security.KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("加载公钥失败: " + publicKeyPath, e);
        }
    }

    /**
     * 解析并验签 JWT Token
     *
     * @param publicKey 公钥
     * @param token     JWT Token
     * @return Claims
     * @throws JwtException 验签失败或 Token 无效
     */
    public static Claims parseAndVerify(PublicKey publicKey, String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验签并返回 Access Token Claims（校验 type=access）
     */
    public static Claims parseAccessToken(PublicKey publicKey, String token) {
        Claims claims = parseAndVerify(publicKey, token);
        String type = claims.get("type", String.class);
        if (!"access".equals(type)) {
            throw new JwtException("Not an access token");
        }
        return claims;
    }

    /**
     * 验证 Token 是否有效
     */
    public static boolean validateToken(PublicKey publicKey, String token) {
        try {
            parseAndVerify(publicKey, token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
