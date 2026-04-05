# my-shortlink

一个基于 `Spring Boot + Spring Cloud + Vue 3` 的 SaaS 短链接系统，采用微服务拆分（`gateway/admin/project`），支持短链创建、跳转、分组管理、回收站、访问统计、JWT 鉴权等能力。

## 架构概览

```text
console-vue (Vite)
   |
   v
gateway (8000)
  |- /api/short-link/admin/** -> admin (8002)
  |- /api/short-link/**       -> project (8001)
  '- /{short-uri}             -> project 短链跳转
```

- `gateway`: 统一入口、JWT 校验、熔断降级、路由转发
- `admin`: 用户与分组管理、短链管理后台、通过 Feign 调用 project
- `project`: 短链核心能力（创建/跳转/统计/回收站）
- `console-vue`: 前端控制台（开发期代理到 `http://127.0.0.1:8000`）

## 核心特性

- 微服务拆分：Gateway + Admin + Project
- 短链跳转链路：多级缓存（Caffeine + Redis）+ Bloom Filter 防穿透
- 数据分片：ShardingSphere（`t_user/t_group/t_link/t_link_goto` 16 分片）
- 鉴权体系：JWT（RS256）+ Gateway 统一鉴权 + Refresh Token
- 容错治理：Gateway 熔断降级（Resilience4j）+ Admin Feign fallback
- 统计链路：本地缓冲 + 定时 flush + Redis Stream 消费 + pending 回收 + 死信
- 可观测性：Sentinel、SkyWalking（可选）

## 技术栈

- Java 17
- Spring Boot 3.0.7 / Spring Cloud 2022.0.3 / Spring Cloud Alibaba 2022.0.0.0-RC2
- MyBatis Plus、ShardingSphere、Redisson、Redis
- Nacos、Sentinel、Resilience4j、OpenFeign
- Vue 3 + Vite + Element Plus

## 目录结构

```text
.
├─ gateway/       # 网关服务
├─ admin/         # 管理后台服务
├─ project/       # 短链核心服务
├─ console-vue/   # 前端控制台
├─ resources/     # SQL 初始化脚本
├─ docker-compose.yml
└─ pom.xml        # Maven 父工程
```

## 运行环境

- JDK 17
- Maven 3.8+
- Node.js 18+
- MySQL 8+
- Docker（可选，用于快速拉起 Nacos/Redis/Sentinel/SkyWalking）

## 快速开始

### 1) 启动基础依赖

```bash
docker-compose up -d
```

`docker-compose.yml` 默认包含：
- Nacos: `XXXX`
- Redis: `XXXX`（密码 `XXXX`）
- Sentinel Dashboard: `XXXX`（容器 XXXX 映射）
- SkyWalking UI: `XXXX`（可选）

### 2) 初始化数据库

1. 在 MySQL 创建数据库：`link`
2. 执行脚本：
   - `resources/database/link.sql`
   - `resources/database/link-data.sql`

> 当前 `*-dev` 分片配置默认使用：
> - host: `127.0.0.1:3306`
> - db: `link`
> - user: `XXXX`
> - password: `XXXX`

### 3) JWT 密钥准备

首次运行建议生成密钥：

```bash
cd admin
bash generate-jwt-keys.sh
```

然后确保 `gateway` 能读取公钥（通常需同步 `public.pem` 到 gateway 资源目录）。

### 4) 编译后端

在仓库根目录：

```bash
./mvnw clean package -DskipTests
```

Windows:

```powershell
.\mvnw.cmd clean package -DskipTests
```

### 5) 启动后端服务（建议顺序）

1. `project`（8001）
2. `admin`（8002）
3. `gateway`（8000）

可使用仓库脚本（含 SkyWalking agent）：

```bash
bash start-project.sh
bash start-admin.sh
bash start-gateway.sh
```

或直接运行 jar：

```bash
java -jar project/target/shortlink-project-1.0-SNAPSHOT.jar
java -jar admin/target/shortlink-admin-1.0-SNAPSHOT.jar
java -jar gateway/target/shortlink-gateway.jar
```

### 6) 启动前端

```bash
cd console-vue
npm install
npm run dev
```

前端开发地址默认：`http://localhost:5173`

## 默认端口

- Gateway: `8000`
- Project: `8001`
- Admin: `8002`
- Frontend(Vite): `5173`

## 默认测试账号

`resources/database/link-data.sql` 中包含初始化账号：

- username: `admin`
- password: `admin123456`

## 常用命令

```bash
# 后端编译
./mvnw clean package -DskipTests

# 仅编译指定模块
./mvnw -pl admin,project,gateway -DskipTests compile

# 前端开发
cd console-vue && npm run dev

# 前端构建
cd console-vue && npm run build
```

## 注意事项

- 当前配置偏开发/演示环境，生产部署需单独 profile（数据库凭据、SQL 日志、Sentinel 规则、密钥管理等）。
- 若遇到 gateway 401，请优先检查：
  1. 是否携带 `Authorization: Bearer <token>`
  2. gateway 公钥是否与 admin 私钥匹配
  3. token 是否过期
- 若遇到服务发现问题，请检查 Nacos 地址是否为 `127.0.0.1:18848`。

---

