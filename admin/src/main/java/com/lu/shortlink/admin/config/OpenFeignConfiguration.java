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

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lu.shortlink.admin.common.biz.user.UserContext;
import com.lu.shortlink.admin.common.convention.exception.RemoteException;
import feign.RequestInterceptor;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * openFeign 微服务调用传递用户信息配置
 */
@Configuration
public class OpenFeignConfiguration {

    // 请求拦截器：http 请求头中加入用户信息
    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            template.header("username", UserContext.getUsername());
            template.header("userId", UserContext.getUserId());
        };
    }

    /**
     * 自定义错误解码器：将 project 服务返回的 Result 错误体转换为 RemoteException，
     * 保留 errorCode 和 errorMessage，避免信息丢失。
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            String errorMessage = extractMessage(response);
            return new RemoteException(StringUtils.hasLength(errorMessage) ? errorMessage : "远程服务调用失败");
        };
    }

    private String extractMessage(Response response) {
        try (Response.Body body = response.body()) {
            if (body == null) {
                return null;
            }
            String bodyStr = new String(body.asInputStream().readAllBytes(), StandardCharsets.UTF_8);
            JSONObject json = JSON.parseObject(bodyStr);
            if (json != null && json.containsKey("message")) {
                return json.getString("message");
            }
        } catch (IOException ignored) {
        }
        return null;
    }
}
