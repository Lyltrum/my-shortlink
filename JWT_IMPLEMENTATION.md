# JWT Token 认证改造文档

> 基于 RS256 非对称签名算法的 JWT 认证方案，替换原有 UUID Token + Redis Hash 机制。

## 目录

- [一、改造背景](#一改造背景)
- [二、密钥生成](#二密钥生成)
- [三、完整链路流程](#三完整链路流程)
  - [1. 首次部署（生成密钥）](#1-首次部署生成密钥)
  - [2. 登录 → 签发 Access + Refresh Token](#2-登录--签发-access--refresh-token)
  - [3. 前端请求 → Gateway JWT 验签](#3-前端请求--gateway-jwt-验签)
  - [4. 后端服务 → 从 Header 获取用户身份](#4-后端服务--从-header-获取用户身份)
  - [5. Token 过期 → Refresh](#5-token-过期--refresh)
  - [6. 退出登录](#6-退出登录)
- [四、接口变更说明](#四接口变更说明)
- [五、配置项说明](#五配置项说明)
- [六、文件改动汇总](#六文件改动汇总)
- [七、部署注意事项](#七部署注意事项)

---

## 一、改造背景

| 对比项 | 改造前（UUID + Redis Hash） | 改造后（JWT + RS256） |
|--------|-----------------------------|----------------------|
| Token 生成 | 服务端生成 UUID，存 Redis Hash | 服务端用 RSA 私钥签名，客户端持有 |
| 签名算法 | 无签名，可伪造 | RS256 非对称签名，防伪造篡改 |
| 状态管理 | 每请求查 Redis，依赖 Redis 可用性 | 无状态，服务端不存 Token |
| 过期控制 | Redis TTL | JWT exp 声明，精确到秒 |
| Refresh | 需重新登录 | Refresh Token 自动续期 |
| 隐私数据 | Redis 存完整用户信息 JSON | JWT Payload 仅含 userId + username |

---

## 二、密钥生成

### 方式一：Shell 脚本（推荐）

```bash
cd admin
bash generate-jwt-keys.sh
```

### 方式二：IDE 运行

在 IDE 中直接运行：
```
com.lu.shortlink.admin.util.JwtKeyGenerator.main()
```

### 生成结果

| 文件 | 路径 | 说明 |
|------|------|------|
| 私钥 | `admin/src/main/resources/keys/private.pem` | **保密**，用于 Admin 服务签发 Token |
| 公钥 | `admin/src/main/resources/keys/public.pem` | 公开，用于 Gateway 验签 |

### 公钥同步到 Gateway

```bash
cp admin/src/main/resources/keys/public.pem \
   gateway/src/main/resources/keys/public.pem
```

---

## 三、完整链路流程

### 1. 首次部署（生成密钥）

```
┌─────────────────────────────────────────────────────────────┐
│  管理员执行 generate-jwt-keys.sh                              │
│                                                              │
│  JwtKeyGenerator.ensureKeyPair()                             │
│    ├─ 检测 keys/private.pem 是否存在                          │
│    ├─ 不存在 → 生成 RSA-2048 密钥对                           │
│    │    ├─ KeyPairGenerator.getInstance("RSA")               │
│    │    └─ Base64 PEM 格式写入文件                           │
│    └─ 存在 → 跳过                                            │
└─────────────────────────────────────────────────────────────┘
```

---

### 2. 登录 → 签发 Access + Refresh Token

```
用户POST /api/short-link/admin/v1/user/login
Body: {"username": "zhangsan", "password": "xxx"}

┌──────────────────────────────────────────────────────────────────┐
│  Gateway（白名单跳过） → Admin 服务                                │
│                                                                  │
│  UserServiceImpl.login()                                          │
│    │                                                             │
│    ├─ 1. 查询 MySQL（ShardingSphere 分片表 t_user_0..15）          │
│    │      WHERE username=? AND password=? AND del_flag=0          │
│    │                                                             │
│    ├─ 2. 获取用户信息                                             │
│    │      userId = userDO.getId()    （Long 类型）               │
│    │      username = userDO.getUsername()                        │
│    │                                                             │
│    ├─ 3. JwtTokenService.generateAccessToken()                   │
│    │      │                                                      │
│    │      └─ JwtUtil.generateAccessToken(privateKey, ...)        │
│    │           │                                                 │
│    │           └─ JJWT Builder:                                  │
│    │                sub = userId（Long.toString）                │
│    │                claims = {username, type:"access"}           │
│    │                iat = now                                   │
│    │                exp = now + 1800秒（30分钟）                 │
│    │                signWith(privateKey, RS256)                  │
│    │                                                             │
│    └─ 4. JwtTokenService.generateRefreshToken()                  │
│             │                                                    │
│             └─ JwtUtil.generateRefreshToken(privateKey, ...)     │
│                  │                                               │
│                  └─ JJWT Builder:                                │
│                       sub = userId                               │
│                       claims = {username, type:"refresh"}        │
│                       exp = now + 604800秒（7天）                 │
│                       signWith(privateKey, RS256)                │
│                                                                  │
│  返回 JSON:                                                      │
│  {                                                               │
│    "accessToken":  "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMi...",    │
│    "refreshToken": "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMi...",   │
│    "expiresIn":    1800                                          │
│  }                                                               │
└──────────────────────────────────────────────────────────────────┘
```

---

### 3. 前端请求 → Gateway JWT 验签

```
前端请求（任一需鉴权的 API）：
  Headers:
    Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMi...

┌────────────────────────────────────────────────────────────────────┐
│  TokenValidateGatewayFilterFactory.apply()                          │
│                                                                     │
│  ┌─ Step 1: 白名单检查 ──────────────────────────────────────────┐  │
│  │                                                               │  │
│  │  白名单路径（application-dev.yaml 配置）:                       │  │
│  │    /api/short-link/admin/v1/user/login    （登录）             │  │
│  │    /api/short-link/admin/v1/user/has-username                  │  │
│  │    /api/short-link/admin/v1/user/refresh   （Refresh）         │  │
│  │    /api/short-link/admin/v1/user           （POST 注册）       │  │
│  │                                                               │  │
│  │  在白名单 → 直接 chain.filter(exchange) 放行                   │  │
│  │  不在白名单 → 进入 JWT 验签流程                                 │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌─ Step 2: 提取 Token ──────────────────────────────────────────┐  │
│  │                                                               │  │
│  │  authHeader = "Bearer eyJhbGciOiJSUzI1NiJ9..."                │  │
│  │  token = authHeader.substring(7)   → JWT 字符串                │  │
│  │                                                               │  │
│  │  缺失或格式错误 → 401 Unauthorized                            │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌─ Step 3: 验签（JwtConfig.init() 启动时已加载公钥）──────────────┐  │
│  │                                                               │  │
│  │  JwtUtil.parseAccessToken(publicKey, token)                    │  │
│  │    │                                                          │  │
│  │    ├─ 1. Jwts.parser().verifyWith(publicKey)                  │  │
│  │    │       用 RSA 公钥解密 Signature 部分                       │  │
│  │    │                                                          │  │
│  │    ├─ 2. 比对 Header + Payload 的哈希是否匹配签名              │  │
│  │    │       签名被篡改 → JwtSignatureException → 401           │  │
│  │    │                                                          │  │
│  │    ├─ 3. 检查 exp 字段                                        │  │
│  │    │       Token 已过期 → ExpiredJwtException → 401           │  │
│  │    │                                                          │  │
│  │    └─ 4. 检查 claims["type"] == "access"                      │  │
│  │            refresh token 尝试访问 → JwtException → 401        │  │
│  │                                                               │  │
│  │  解析成功返回 Claims:                                          │  │
│  │    { sub: "123", username: "zhangsan", type: "access",         │  │
│  │      iat: 1712000000, exp: 1712001800 }                       │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌─ Step 4: 注入下游 Header ──────────────────────────────────────┐  │
│  │                                                               │  │
│  │  exchange.mutate().request(builder)                            │  │
│  │    Headers:                                                   │  │
│  │      userId = claims.getSubject()    → "123"                 │  │
│  │      username = URLEncode(claims["username"])                  │  │
│  │                                                               │  │
│  │  路由到 Admin 或 Project 后端                                  │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  验签失败路径:                                                      │
│    └─ writeError() → 401 Unauthorized                              │
│         { status: 401, message: "Invalid or expired token" }        │
└────────────────────────────────────────────────────────────────────┘
```

---

### 4. 后端服务 → 从 Header 获取用户身份

```
┌──────────────────────────────────────────────────────────────────┐
│  Admin / Project 后端                                              │
│                                                                   │
│  UserTransmitFilter.doFilter()                                     │
│    │                                                             │
│    ├─ 读取 Header: userId, username                              │
│    │                                                             │
│    ├─ UserInfoDTO userInfoDTO = new UserInfoDTO(userId, username) │
│    │                                                             │
│    ├─ UserContext.setUser(userInfoDTO)                            │
│    │      └─ 存入 Alibaba TTL ThreadLocal                        │
│    │                                                             │
│    ├─ filterChain.doFilter() 执行业务逻辑                        │
│    │                                                             │
│    └─ finally: UserContext.removeUser() 清理                      │
│                                                                   │
│  业务代码中获取用户:                                               │
│    String userId = UserContext.getUserId();   → "123"            │
│    String username = UserContext.getUsername(); → "zhangsan"      │
│                                                                   │
│  说明: Gateway 已完成 JWT 签名验证，后端无需再验签。                 │
└──────────────────────────────────────────────────────────────────┘
```

---

### 5. Token 过期 → Refresh

```
前端请求：
  POST /api/short-link/admin/v1/user/refresh
  Body: refreshToken=eyJhbGciOiJSUzI1NiJ9...

┌──────────────────────────────────────────────────────────────────┐
│  Gateway 白名单放行 → Admin 服务                                   │
│                                                                   │
│  UserServiceImpl.refreshAccessToken()                              │
│    │                                                             │
│    ├─ 1. JwtTokenService.parseRefreshToken(refreshToken)          │
│    │      │                                                      │
│    │      └─ JwtUtil.parseRefreshToken(publicKey, token)          │
│    │           ├─ RS256 签名验证                                  │
│    │           ├─ exp 过期检查                                    │
│    │           └─ type == "refresh" 校验                          │
│    │               type 不匹配 → JwtException                      │
│    │                                                             │
│    ├─ 2. 从 Claims 提取 userId、username                         │
│    │                                                             │
│    ├─ 3. 签发新 Token 对                                          │
│    │      accessToken  = generateAccessToken(userId, username)    │
│    │      refreshToken = generateRefreshToken(userId, username)    │
│    │                                                             │
│    └─ 返回: {accessToken, refreshToken, expiresIn}                 │
│                                                                   │
│  注意: 旧 Refresh Token 仍然有效。可选升级为一次性黑名单机制：         │
│        Refresh 时将旧 Token 加入 Redis 黑名单 set。                 │
└──────────────────────────────────────────────────────────────────┘
```

---

### 6. 退出登录

```
前端请求：
  DELETE /api/short-link/admin/v1/user/logout
  Body: accessToken=eyJhbGciOiJSUzI1NiJ9...

┌──────────────────────────────────────────────────────────────────┐
│  UserServiceImpl.logout()                                         │
│    │                                                             │
│    └─ 空实现（JWT 无状态，服务端不维护 Token 状态）                 │
│                                                                   │
│  说明:                                                            │
│    - JWT 无状态，服务端无需存储 Token                               │
│    - 客户端自行删除 localStorage / sessionStorage 中的 Token       │
│    - 若需强制失效（Token 被盗），可加 Redis 黑名单：                 │
│        if (jwtBlacklist.contains(token)) throw Exception;         │
└──────────────────────────────────────────────────────────────────┘
```

---

## 四、接口变更说明

### 登录接口

**POST** `/api/short-link/admin/v1/user/login`

> 请求 Body 不变，响应结构变更。

**旧响应：**
```json
{ "token": "550e8400-e29b-41d4-a716-446655440000" }
```

**新响应：**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJSUzI1NiJ9...",
  "expiresIn": 1800
}
```

### Refresh Token 接口（新增）

**POST** `/api/short-link/admin/v1/user/refresh`

| 参数 | 类型 | 说明 |
|------|------|------|
| refreshToken | String | Refresh Token |

### 检查登录接口

**GET** `/api/short-link/admin/v1/user/check-login`

| 参数 | 旧 | 新 |
|------|----|----|
| username | 必需 | -（从 JWT Claims 获取） |
| token | 必需 | **accessToken** |

### 退出登录接口

**DELETE** `/api/short-link/admin/v1/user/logout`

| 参数 | 旧 | 新 |
|------|----|----|
| username | 必需 | - |
| token | 必需 | **accessToken** |

---

## 五、配置项说明

### Admin 模块（application.yaml）

```yaml
short-link:
  jwt:
    access-token-ttl: 1800      # Access Token 有效期（秒），默认 30 分钟
    refresh-token-ttl: 604800   # Refresh Token 有效期（秒），默认 7 天
    private-key-path: classpath:keys/private.pem   # RSA 私钥
    public-key-path: classpath:keys/public.pem     # RSA 公钥
```

### Gateway 模块（application.yaml）

```yaml
short-link:
  jwt:
    public-key-path: classpath:keys/public.pem   # RSA 公钥（Gateway 只验签）
```

### Gateway 路由白名单（application-dev.yaml）

```yaml
filters:
  - name: TokenValidate
    args:
      whitePathList:
        - /api/short-link/admin/v1/user/login      # 登录
        - /api/short-link/admin/v1/user/has-username  # 用户名检查
        - /api/short-link/admin/v1/user/refresh    # Token 刷新
        - /api/short-link/admin/v1/user           # POST 注册
```

---

## 六、文件改动汇总

### 新增文件（admin 模块）

| 文件路径 | 作用 |
|----------|------|
| `util/JwtKeyGenerator.java` | RSA-2048 密钥对生成工具 |
| `util/JwtUtil.java` | JWT 签名/验签核心逻辑（RS256） |
| `config/JwtProperties.java` | JWT 配置属性绑定类 |
| `service/JwtTokenService.java` | Token 业务封装，启动时加载密钥 |
| `resources/keys/private.pem` | RSA 私钥占位符（部署时替换） |
| `resources/keys/public.pem` | RSA 公钥占位符（部署时替换） |
| `generate-jwt-keys.sh` | 一键生成密钥脚本 |
| `JWT_KEYS_README.txt` | 密钥部署说明 |

### 新增文件（gateway 模块）

| 文件路径 | 作用 |
|----------|------|
| `config/JwtProperties.java` | JWT 配置属性（公钥路径） |
| `config/JwtConfig.java` | 启动时加载公钥 |
| `util/JwtUtil.java` | Gateway 专用验签工具（仅公钥验签） |
| `resources/keys/public.pem` | RSA 公钥占位符 |

### 修改文件

| 模块 | 文件 | 改动说明 |
|------|------|----------|
| admin | `pom.xml` | 新增 jjwt-api / jjwt-impl / jjwt-jackson 依赖 |
| admin | `dto/resp/UserLoginRespDTO.java` | 字段改为 accessToken + refreshToken + expiresIn |
| admin | `service/UserService.java` | 接口签名改为 JWT 风格 |
| admin | `service/impl/UserServiceImpl.java` | 移除 Redis Hash 存储，改为 JWT 签名 |
| admin | `controller/UserController.java` | 新增 `/refresh` 接口，logout/checkLogin 参数变更 |
| admin | `resources/application.yaml` | 新增 `short-link.jwt.*` 配置 |
| gateway | `pom.xml` | 新增 jjwt-api / jjwt-impl / jjwt-jackson 依赖 |
| gateway | `filter/TokenValidateGatewayFilterFactory.java` | 移除 Redis Hash 逻辑，改为 JWT RS256 验签 |
| gateway | `resources/application.yaml` | 新增 `short-link.jwt.*` 配置 |
| gateway | `resources/application-dev.yaml` | 更新路由白名单 |

---

## 七、部署注意事项

### 1. 生成密钥（首次必须）

```bash
cd admin
bash generate-jwt-keys.sh
```

### 2. 同步公钥到 Gateway

```bash
cp admin/src/main/resources/keys/public.pem \
   gateway/src/main/resources/keys/public.pem
```

### 3. 私钥保密

`private.pem` **严禁提交到 Git**。建议在 `.gitignore` 中添加：

```
admin/src/main/resources/keys/private.pem
```

### 4. 生产环境密钥管理

生产环境建议使用密钥管理服务（KMS / Vault）存储私钥，而非文件系统。可修改 `JwtTokenService.init()` 从 KMS 获取密钥。

### 5. Token 强制失效（如需）

若 Token 泄漏需强制失效，可在 Redis 中维护黑名单：

```java
// Refresh 时将旧 refresh token 加入黑名单
if (refreshTokenUsedBefore(token)) {
    throw new ClientException("Token已被使用，请重新登录");
}
redisTemplate.opsForSet().add("jwt:blacklist:", token);
```

---

## 八、JWT 结构说明

```
eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMiLCJ1c2VybmFtZSI6InpoYW5nc2FuIiwidHlwZSI6ImFjY2VzcyIsImlhdCI6MTcxMjAwMDAwMCwiZXhwIjoxNzEyMDAxODAwfQ.XXXX_SIGNATURE_XXXX

┌──────────────────┬─────────────────────────────────────────────────┬──────────────────┐
│    Header        │                    Payload                      │    Signature     │
│  (base64url)     │                   (base64url)                    │   (base64url)    │
├──────────────────┼─────────────────────────────────────────────────┼──────────────────┤
│  alg: RS256      │  sub: "123"       ← userId                      │                  │
│  typ: JWT        │  username: "zhangsan"                            │  RSA-SHA256      │
│                  │  type: "access"    ← 区分 access/refresh token   │  私钥签名        │
│                  │  iat: 1712000000  ← 签发时间（Unix ms）          │  公钥验签        │
│                  │  exp: 1712001800  ← 过期时间（Unix ms）          │                  │
└──────────────────┴─────────────────────────────────────────────────┴──────────────────┘
```
