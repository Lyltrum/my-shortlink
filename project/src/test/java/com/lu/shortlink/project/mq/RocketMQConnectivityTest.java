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

package com.lu.shortlink.project.mq;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RocketMQ 连通性集成测试，不依赖 Spring 上下文，直接测试 namesrv + broker 是否可达。
 * 运行前提：rmqnamesrv 和 rmqbroker 容器正常运行，宿主机端口 9876 可达。
 */
class RocketMQConnectivityTest {

    private static final String NAMESRV_ADDR = "127.0.0.1:9876";
    private static final String TEST_TOPIC   = "short-link-stats-topic";
    private static final String PRODUCER_GROUP = "test-producer-group-" + UUID.randomUUID();
    private static final String CONSUMER_GROUP = "test-consumer-group-" + UUID.randomUUID();

    private DefaultMQProducer producer;
    private DefaultMQPushConsumer consumer;

    @BeforeEach
    void setUp() throws Exception {
        producer = new DefaultMQProducer(PRODUCER_GROUP);
        producer.setNamesrvAddr(NAMESRV_ADDR);
        producer.setSendMsgTimeout(5000);
        producer.start();
    }

    @AfterEach
    void tearDown() {
        if (producer != null) producer.shutdown();
        if (consumer != null) consumer.shutdown();
    }

    // ── 测试 1：Namesrv 连通 + 消息发送 ──────────────────────────────────────────

    @Test
    void testProducerCanSendMessage() throws Exception {
        String payload = "ping-" + System.currentTimeMillis();
        Message msg = new Message(TEST_TOPIC, payload.getBytes(StandardCharsets.UTF_8));

        SendResult result = producer.send(msg);

        assertNotNull(result, "SendResult 不应为 null");
        assertSame(SendStatus.SEND_OK, result.getSendStatus(),
                "发送状态应为 SEND_OK，实际：" + result.getSendStatus());
        System.out.println("[✓] 消息发送成功 msgId=" + result.getMsgId()
                + "  queue=" + result.getMessageQueue());
    }

    // ── 测试 2：完整发布/订阅链路 ───────────────────────────────────────────────

    @Test
    void testSendAndReceive() throws Exception {
        String expected = "rocketmq-test-" + UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();

        // 先启动消费者再发消息，避免竞态
        consumer = new DefaultMQPushConsumer(CONSUMER_GROUP);
        consumer.setNamesrvAddr(NAMESRV_ADDR);
        consumer.subscribe(TEST_TOPIC, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
            String body = new String(msgs.get(0).getBody(), StandardCharsets.UTF_8);
            // 只关心本次测试发出的消息（UUID 唯一）
            if (body.equals(expected)) {
                received.set(body);
                latch.countDown();
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();

        // 稍等消费者完成注册
        Thread.sleep(1000);

        Message msg = new Message(TEST_TOPIC, expected.getBytes(StandardCharsets.UTF_8));
        SendResult result = producer.send(msg);
        assertSame(SendStatus.SEND_OK, result.getSendStatus());

        boolean arrived = latch.await(15, TimeUnit.SECONDS);
        assertTrue(arrived, "15 秒内未收到消息，检查 broker brokerIP1 配置或网络");
        assertEquals(expected, received.get(), "收到的消息内容与发送内容不一致");

        System.out.println("[✓] 发布/订阅链路验证通过  payload=" + received.get());
    }
}
