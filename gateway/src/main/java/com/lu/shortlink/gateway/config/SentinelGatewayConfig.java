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

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.fastjson2.JSON;
import com.lu.shortlink.gateway.dto.GatewayErrorResult;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sentinel Gateway 限流配置
 * 保护登录和注册接口，防止暴力破解和恶意批量注册
 */
@Configuration
public class SentinelGatewayConfig {

    private final List<ViewResolver> viewResolvers;
    private final ServerCodecConfigurer serverCodecConfigurer;

    public SentinelGatewayConfig(ObjectProvider<List<ViewResolver>> viewResolversProvider,
                                  ServerCodecConfigurer serverCodecConfigurer) {
        this.viewResolvers = viewResolversProvider.getIfAvailable(Collections::emptyList);
        this.serverCodecConfigurer = serverCodecConfigurer;
    }

    /**
     * 捕获 BlockException，优先级最高
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }

    /**
     * Sentinel 全局过滤器，在路由前执行限流检查
     */
    @Bean
    @Order(-1)
    public GlobalFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    @PostConstruct
    public void doInit() {
        initBlockHandler();
        initApiDefinitions();
        initGatewayRules();
    }

    /**
     * 自定义被限流时的响应格式，与现有 GatewayErrorResult 保持一致，返回 429
     */
    private void initBlockHandler() {
        GatewayCallbackManager.setBlockHandler((exchange, t) -> {
            GatewayErrorResult result = GatewayErrorResult.builder()
                    .status(HttpStatus.TOO_MANY_REQUESTS.value())
                    .message("Request frequency is too high, please try again later.")
                    .build();
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(JSON.toJSONString(result));
        });
    }

    /**
     * 定义需要独立限流的 API 分组
     * 使用精确匹配，避免影响同前缀的其他接口
     */
    private void initApiDefinitions() {
        Set<ApiDefinition> definitions = new HashSet<>();

        definitions.add(new ApiDefinition("login-api")
                .setPredicateItems(new HashSet<>() {{
                    add(new ApiPathPredicateItem()
                            .setPattern("/api/short-link/admin/v1/user/login")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT));
                }}));

        definitions.add(new ApiDefinition("register-api")
                .setPredicateItems(new HashSet<>() {{
                    add(new ApiPathPredicateItem()
                            .setPattern("/api/short-link/admin/v1/user")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT));
                }}));

        GatewayApiDefinitionManager.loadApiDefinitions(definitions);
    }

    /**
     * 初始化限流规则
     * login-api：5 QPS，暴力破解场景下真人根本触发不到
     * register-api：2 QPS，正常注册场景极低频，恶意批量注册会被拦截
     */
    private void initGatewayRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        rules.add(new GatewayFlowRule("login-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(5)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT));

        rules.add(new GatewayFlowRule("register-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(2)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT));

        GatewayRuleManager.loadRules(rules);
    }
}
