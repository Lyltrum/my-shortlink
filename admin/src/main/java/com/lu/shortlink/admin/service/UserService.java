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

import com.baomidou.mybatisplus.extension.service.IService;
import com.lu.shortlink.admin.dao.entity.UserDO;
import com.lu.shortlink.admin.dto.req.UserLoginReqDTO;
import com.lu.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.lu.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.lu.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.lu.shortlink.admin.dto.resp.UserRespDTO;

/**
 * 用户接口层
 */
public interface UserService extends IService<UserDO> {

    /**
     * 根据用户名查询用户信息
     *
     * @param username 用户名
     * @return 用户返回实体
     */
    UserRespDTO getUserByUsername(String username);

    /**
     * 查询用户名是否存在
     *
     * @param username 用户名
     * @return 用户名存在返回 True，不存在返回 False
     */
    Boolean hasUsername(String username);

    /**
     * 注册用户
     *
     * @param requestParam 注册用户请求参数
     */
    void register(UserRegisterReqDTO requestParam);

    /**
     * 根据用户名修改用户
     *
     * @param requestParam 修改用户请求参数
     */
    void update(UserUpdateReqDTO requestParam);

    /**
     * 用户登录，返回 Access Token + Refresh Token
     *
     * @param requestParam 用户登录请求参数
     * @return 用户登录返回参数
     */
    UserLoginRespDTO login(UserLoginReqDTO requestParam);

    /**
     * 使用 Refresh Token 换取新的 Access Token
     *
     * @param refreshToken Refresh Token
     * @return 新的登录返回参数
     */
    UserLoginRespDTO refreshAccessToken(String refreshToken);

    /**
     * 检查 Access Token 是否有效
     *
     * @param accessToken Access Token
     * @return Token 有效返回 True
     */
    Boolean checkLogin(String accessToken);

    /**
     * 退出登录（JWT 无状态，客户端丢弃 Token 即可，服务端可记录黑名单）
     *
     * @param accessToken Access Token
     */
    void logout(String accessToken);
}
