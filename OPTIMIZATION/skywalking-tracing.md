# SkyWalking 分布式链路追踪集成计划

## Context

当前三个服务（gateway/admin/project）之间调用没有 TraceId 贯穿，线上排查问题困难。需要接入 SkyWalking Agent（无侵入，零代码修改），实现调用链路可视化：Gateway → Admin → Project，并自动追踪 MySQL / Redis / Feign 调用。

---

## 方案概述

```
[host] gateway:8000 --javaagent--> [Docker] OAP:11800
[host] admin:8002   --javaagent-->           ↓
[host] project:8001 --javaagent-->  [Docker] UI:8888 (http://localhost:8888)
```

- OAP + UI 在 Docker 中运行（加入现有 docker-compose.yml）
- Java Agent 放在项目根目录 `skywalking-agent/`
- 三个服务启动时通过 `-javaagent` 挂载，通过 `-Dskywalking.*` 传参，**不修改任何 Java 代码**

---

## 版本选择

| 组件 | 版本 | 说明 |
|------|------|------|
| SkyWalking OAP Server | 9.7.0 | Docker 镜像 `apache/skywalking-oap-server:9.7.0` |
| SkyWalking UI | 9.7.0 | Docker 镜像 `apache/skywalking-ui:9.7.0` |
| SkyWalking Java Agent | 9.3.0 | 支持 Spring Boot 3 / Jakarta EE，独立版本号 |

Spring Boot 3.0.7 使用 `jakarta.*` 命名空间，必须用 9.x Agent。

---

## 实施步骤

### Step 1 — 修改 docker-compose.yml

在 `D:\CODE_shortlink\shortlink\docker-compose.yml` 的 `services:` 块末尾追加：

```yaml
  skywalking-oap:
    image: apache/skywalking-oap-server:9.7.0
    container_name: skywalking-oap
    environment:
      - SW_STORAGE=h2
      - JAVA_OPTS=-Xmx512m
    ports:
      - "11800:11800"   # gRPC — Agent 上报
      - "12800:12800"   # REST — UI 查询
    networks:
      - shortlink-net
    restart: unless-stopped

  skywalking-ui:
    image: apache/skywalking-ui:9.7.0
    container_name: skywalking-ui
    depends_on:
      - skywalking-oap
    environment:
      - SW_OAP_ADDRESS=http://skywalking-oap:12800
    ports:
      - "8888:8080"     # 访问地址：http://localhost:8888
    networks:
      - shortlink-net
    restart: unless-stopped
```

**说明：** `SW_STORAGE=h2` 使用内存存储，无需 Elasticsearch，适合本地开发。OAP 重启后数据清空。

启动：
```bash
docker-compose up -d skywalking-oap skywalking-ui
```
等待约 30 秒，访问 `http://localhost:8888` 验证 UI 已启动。

---

### Step 2 — 下载并解压 Java Agent

在项目根目录执行（需要下载约 50MB 的压缩包）：

```bash
cd D:/CODE_shortlink/shortlink

# 下载
curl -O https://archive.apache.org/dist/skywalking/java-agent/9.3.0/apache-skywalking-java-agent-9.3.0.tgz

# 解压到 skywalking-agent/ 目录
mkdir -p skywalking-agent
tar -xzf apache-skywalking-java-agent-9.3.0.tgz -C skywalking-agent --strip-components=1

# 删除压缩包
rm apache-skywalking-java-agent-9.3.0.tgz
```

解压后结构：
```
skywalking-agent/
├── skywalking-agent.jar        ← -javaagent 目标
├── config/agent.config         ← 主配置文件
├── plugins/                    ← 激活的插件
├── optional-plugins/           ← 未激活的插件（需手动复制到 plugins/）
└── logs/                       ← Agent 运行日志
```

---

### Step 3 — 激活必要的可选插件

Spring Cloud Gateway 是响应式网关，其插件默认在 `optional-plugins/` 中，需手动激活：

```bash
cd D:/CODE_shortlink/shortlink/skywalking-agent

# Spring Cloud Gateway 4.x 插件（响应式网关透传 TraceId 必须）
cp optional-plugins/apm-spring-cloud-gateway-4.x-plugin-*.jar plugins/ 2>/dev/null || true
cp optional-plugins/apm-spring-cloud-gateway-3.x-plugin-*.jar plugins/ 2>/dev/null || true
```

确认以下插件已在 `plugins/` 中（默认应已存在，检查一下）：
- `apm-feign-default-http-9.x-plugin-*.jar`（Feign 调用追踪）
- `apm-lettuce-5.x-plugin-*.jar`（Redis Lettuce 客户端追踪）
- `apm-mysql-8.x-plugin-*.jar`（MySQL 追踪）
- `apm-mybatis-3.x-plugin-*.jar`（MyBatis 追踪）

---

### Step 4 — 修改 agent.config

文件路径：`D:\CODE_shortlink\shortlink\skywalking-agent\config\agent.config`

找到以下配置行并修改（其他保持默认）：

```properties
# OAP 地址 — Agent 运行在 host，OAP 在 Docker 映射到 host:11800
collector.backend_service=${SW_AGENT_COLLECTOR_BACKEND_SERVICES:127.0.0.1:11800}

# 命名空间（可选，用于在 UI 中分组）
agent.namespace=${SW_AGENT_NAMESPACE:shortlink}

# 日志级别
logging.level=${SW_LOGGING_LEVEL:INFO}
```

**注意：** `agent.service_name` 不需要在这里改，每个服务通过 `-D` 参数单独传入（见 Step 5）。

---

### Step 5 — 启动三个服务（加 Agent）

先构建（如果还没构建）：
```bash
./mvnw clean package -DskipTests
```

**启动顺序：先 admin/project，再 gateway（与原来相同）。**

#### Gateway（port 8000）
```bash
java -javaagent:D:/CODE_shortlink/shortlink/skywalking-agent/skywalking-agent.jar \
     -Dskywalking.agent.service_name=short-link-gateway \
     -Dskywalking.collector.backend_service=127.0.0.1:11800 \
     -jar D:/CODE_shortlink/shortlink/gateway/target/shortlink-gateway.jar
```

#### Admin（port 8002）
```bash
java -javaagent:D:/CODE_shortlink/shortlink/skywalking-agent/skywalking-agent.jar \
     -Dskywalking.agent.service_name=short-link-admin \
     -Dskywalking.collector.backend_service=127.0.0.1:11800 \
     -jar D:/CODE_shortlink/shortlink/admin/target/shortlink-admin-1.0-SNAPSHOT.jar
```

#### Project（port 8001）
```bash
java -javaagent:D:/CODE_shortlink/shortlink/skywalking-agent/skywalking-agent.jar \
     -Dskywalking.agent.service_name=short-link-project \
     -Dskywalking.collector.backend_service=127.0.0.1:11800 \
     -jar D:/CODE_shortlink/shortlink/project/target/shortlink-project-1.0-SNAPSHOT.jar
```

---

### Step 6 — 把启动命令保存为脚本（可选但推荐）

在项目根创建三个启动脚本：`start-gateway.sh`、`start-admin.sh`、`start-project.sh`，方便以后使用。

---

## 验证方法

### 1. Agent 是否连接成功
查看 `skywalking-agent/logs/skywalking-api.log`，应出现：
```
INFO ... - grpc channel connected
```

### 2. 服务是否出现在 UI
访问 `http://localhost:8888` → General Service，服务列表中应出现 `short-link-gateway`、`short-link-admin`、`short-link-project`（第一次请求后才会出现）。

### 3. 端到端链路追踪
发送一个登录请求（Gateway → Admin）：
```bash
curl -X POST http://localhost:8000/api/short-link/admin/v1/user/login \
     -H "Content-Type: application/json" \
     -d '{"username":"xxx","password":"xxx"}'
```

在 UI 中：Trace → 选时间范围 "Last 15 min" → 选服务 `short-link-gateway` → 点击 Trace 条目
应看到包含多个 Span 的火焰图：Gateway filter → Admin HTTP → MySQL/Redis 子 Span。

### 4. 拓扑图
UI → Topology：应出现 gateway、admin、project、MySQL、Redis 节点及连线。

---

## 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| Gateway 追踪只有一个 Span，无下游 | Gateway 可选插件未激活 | 复制 `apm-spring-cloud-gateway-4.x-plugin` 到 `plugins/` |
| `DISCONNECTED` 循环出现在 Agent 日志 | OAP 未启动或端口未映射 | `docker ps` 确认 11800 端口已映射 |
| OAP 容器反复重启 | 内存不足 | `JAVA_OPTS=-Xmx1g` 或增大 Docker Desktop 内存 |
| 服务名显示为 `unknown-service` | `-Dskywalking.agent.service_name` 未生效 | 确认 `-D` 参数在 `-javaagent` 之后、`-jar` 之前 |

---

## 修改的文件

- `D:\CODE_shortlink\shortlink\docker-compose.yml` — 追加 OAP + UI 服务
- `D:\CODE_shortlink\shortlink\skywalking-agent\config\agent.config` — 设置 OAP 地址（新文件，解压后创建）
- （可选）`start-gateway.sh` / `start-admin.sh` / `start-project.sh` — 封装启动命令

**无需修改任何 Java 代码。**
