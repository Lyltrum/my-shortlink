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

package com.lu.shortlink.admin.remote.fallback;

import com.lu.shortlink.admin.common.convention.errorcode.BaseErrorCode;
import com.lu.shortlink.admin.common.convention.result.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShortLinkRemoteServiceFallbackFactoryTest {

    @Test
    void createShortLinkShouldReturnRemoteFailureResult() {
        ShortLinkRemoteServiceFallbackFactory fallbackFactory = new ShortLinkRemoteServiceFallbackFactory();

        Result<?> result = fallbackFactory.create(new RuntimeException("timeout"))
                .createShortLink(null);

        assertNotNull(result);
        assertEquals(BaseErrorCode.REMOTE_ERROR.code(), result.getCode());
        assertEquals("短链接服务暂时不可用，请稍后重试", result.getMessage());
        assertNull(result.getData());
    }
}
