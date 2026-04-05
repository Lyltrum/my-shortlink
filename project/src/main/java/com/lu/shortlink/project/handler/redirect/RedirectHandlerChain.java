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

package com.lu.shortlink.project.handler.redirect;

/**
 * 责任链推进接口（函数式，可用 lambda 表达）
 *
 * <p>线程安全保证：链通过 lambda 右折叠方式构建，每个节点捕获的是不可变的下一个 Chain 引用；
 * 请求上下文 {@link RedirectContext} 在 restoreUrl() 中每次 new，不在链节点间共享，
 * 因此链本身完全无状态，满足高并发安全要求。
 */
@FunctionalInterface
public interface RedirectHandlerChain {

    void proceed(RedirectContext ctx);
}
