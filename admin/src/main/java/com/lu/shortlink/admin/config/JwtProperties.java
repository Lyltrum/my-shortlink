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

package com.lu.shortlink.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "short-link.jwt")
public class JwtProperties {

    /**
     * Access Token 有效期（秒）
     */
    private long accessTokenTtl = 1800;

    /**
     * Refresh Token 有效期（秒）
     */
    private long refreshTokenTtl = 604800;

    /**
     * RSA 私钥路径（classpath）
     */
    private String privateKeyPath;

    /**
     * RSA 公钥路径（classpath）
     */
    private String publicKeyPath;
}
