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

package com.lu.shortlink.project.handler.redirect.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lu.shortlink.project.dao.entity.ShortLinkDO;
import com.lu.shortlink.project.dao.entity.ShortLinkGotoDO;
import com.lu.shortlink.project.dao.mapper.ShortLinkGotoMapper;
import com.lu.shortlink.project.dao.mapper.ShortLinkMapper;
import com.lu.shortlink.project.handler.redirect.RedirectContext;
import com.lu.shortlink.project.handler.redirect.RedirectHandler;
import com.lu.shortlink.project.handler.redirect.RedirectHandlerChain;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.lu.shortlink.project.common.constant.RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY;

/**
 * 责任链节点 6：数据库查询（路由表 + 主表 + 有效期校验）
 *
 * <p>任一步骤失败（goto 不存在、主表不存在、链接已过期）时：
 * 写入空值缓存（TTL 30 分钟）并 404，中止链。
 * 成功时：将 gid / originUrl / validDate 写入 ctx，调用 chain.proceed(ctx)。
 */
@Component
@RequiredArgsConstructor
public class DbLookupHandler implements RedirectHandler {

    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final ShortLinkMapper shortLinkMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @SneakyThrows
    @Override
    public void handle(RedirectContext ctx, RedirectHandlerChain chain) {
        String fullShortUrl = ctx.getFullShortUrl();

        // 1. 查路由表，获取 gid
        LambdaQueryWrapper<ShortLinkGotoDO> gotoQuery = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
        ShortLinkGotoDO gotoDO = shortLinkGotoMapper.selectOne(gotoQuery);
        if (gotoDO == null) {
            // Bloom Filter 误判：写空值缓存兜底
            stringRedisTemplate.opsForValue()
                    .set(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl), "-", 30, TimeUnit.MINUTES);
            ctx.getResponse().sendRedirect("/page/notfound");
            return;
        }

        // 2. 查主表
        LambdaQueryWrapper<ShortLinkDO> linkQuery = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, gotoDO.getGid())
                .eq(ShortLinkDO::getFullShortUrl, fullShortUrl)
                .eq(ShortLinkDO::getDelFlag, 0)
                .eq(ShortLinkDO::getEnableStatus, 0);
        ShortLinkDO linkDO = shortLinkMapper.selectOne(linkQuery);

        // 3. 有效期校验（主表不存在或已过期）
        if (linkDO == null || (linkDO.getValidDate() != null && linkDO.getValidDate().before(new Date()))) {
            stringRedisTemplate.opsForValue()
                    .set(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, fullShortUrl), "-", 30, TimeUnit.MINUTES);
            ctx.getResponse().sendRedirect("/page/notfound");
            return;
        }

        // 4. 填充上下文，供后续节点使用
        ctx.setResolvedGid(gotoDO.getGid());
        ctx.setResolvedOriginUrl(linkDO.getOriginUrl());
        ctx.setResolvedValidDate(linkDO.getValidDate());

        chain.proceed(ctx);
    }
}
