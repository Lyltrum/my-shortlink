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
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtUtilTokenVersionTest {

    @Test
    void generatedAccessTokenShouldContainTokenVersionClaim() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        String token = JwtUtil.generateAccessToken(keyPair.getPrivate(), "1001", "tom", 3L, 60_000L);
        Claims claims = JwtUtil.parseAccessToken(keyPair.getPublic(), token);

        assertEquals("1001", claims.getSubject());
        assertEquals("tom", claims.get("username", String.class));
        assertEquals(3L, claims.get("tokenVersion", Number.class).longValue());
    }
}

