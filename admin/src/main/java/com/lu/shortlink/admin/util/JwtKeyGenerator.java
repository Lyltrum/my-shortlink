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

import cn.hutool.core.io.FileUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 密钥对生成工具
 * <p>
 * 启动时自动检测密钥文件是否存在，不存在则自动生成。
 * 生成后请妥善保管私钥文件，勿泄漏。
 * </p>
 */
public class JwtKeyGenerator {

    private static final int KEY_SIZE = 2048;

    public static void main(String[] args) {
        String dir = "src/main/resources/keys";
        String publicKeyPath = dir + "/public.pem";
        String privateKeyPath = dir + "/private.pem";

        if (!FileUtil.exist(publicKeyPath) || !FileUtil.exist(privateKeyPath)) {
            generateKeyPair(publicKeyPath, privateKeyPath);
            System.out.println("RSA 密钥对生成完毕，请妥善保管私钥文件: " + privateKeyPath);
        } else {
            System.out.println("密钥文件已存在，跳过生成步骤。");
        }
    }

    public static void ensureKeyPair(String publicKeyPath, String privateKeyPath) {
        if (!FileUtil.exist(publicKeyPath) || !FileUtil.exist(privateKeyPath)) {
            File dir = new File("src/main/resources/keys");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            generateKeyPair(publicKeyPath, privateKeyPath);
        }
    }

    private static void generateKeyPair(String publicKeyPath, String privateKeyPath) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(KEY_SIZE, new SecureRandom());
            KeyPair pair = keyGen.generateKeyPair();

            PublicKey pub = pair.getPublic();
            PrivateKey pri = pair.getPrivate();

            String pubKeyContent = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes())
                            .encodeToString(pub.getEncoded())
                    + "\n-----END PUBLIC KEY-----";
            String priKeyContent = "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes())
                            .encodeToString(pri.getEncoded())
                    + "\n-----END PRIVATE KEY-----";

            FileUtil.writeString(pubKeyContent, publicKeyPath, StandardCharsets.UTF_8);
            FileUtil.writeString(priKeyContent, privateKeyPath, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("生成 RSA 密钥对失败", e);
        }
    }

    public static PublicKey loadPublicKey(String path) {
        try {
            String key = FileUtil.readString(path, StandardCharsets.UTF_8)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            X509EncodedKeySpec spec = new X509EncodedKeySpec(Base64.getDecoder().decode(key));
            return java.security.KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("加载公钥失败", e);
        }
    }

    public static PublicKey loadPublicKey(java.io.InputStream in) {
        try {
            String key = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            X509EncodedKeySpec spec = new X509EncodedKeySpec(Base64.getDecoder().decode(key));
            return java.security.KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("加载公钥失败", e);
        }
    }

    public static PrivateKey loadPrivateKey(String path) {
        try {
            String key = FileUtil.readString(path, StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(key));
            return java.security.KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("加载私钥失败", e);
        }
    }

    public static PrivateKey loadPrivateKey(java.io.InputStream in) {
        try {
            String key = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(key));
            return java.security.KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("加载私钥失败", e);
        }
    }
}
