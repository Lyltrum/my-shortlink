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

package com.lu.shortlink.admin.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类，支持 RS256 签名与验签
 */
public class JwtUtil {

    private JwtUtil() {
    }

    public static String generateAccessToken(PrivateKey privateKey, String userId, String username, long expirationMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(userId)
                .claims(Map.of(
                        "username", username,
                        "type", "access"
                ))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public static String generateRefreshToken(PrivateKey privateKey, String userId, String username, long expirationMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(userId)
                .claims(Map.of(
                        "username", username,
                        "type", "refresh"
                ))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public static Claims parseToken(PublicKey publicKey, String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static boolean validateToken(PublicKey publicKey, String token) {
        try {
            parseToken(publicKey, token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public static Claims parseAccessToken(PublicKey publicKey, String token) {
        Claims claims = parseToken(publicKey, token);
        String type = claims.get("type", String.class);
        if (!"access".equals(type)) {
            throw new JwtException("Not an access token");
        }
        return claims;
    }

    public static Claims parseRefreshToken(PublicKey publicKey, String token) {
        Claims claims = parseToken(publicKey, token);
        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new JwtException("Not a refresh token");
        }
        return claims;
    }
}
