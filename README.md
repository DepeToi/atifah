# atiFah

情绪管理高并发微服务项目（App + 管理后台），基于 Spring Boot / Spring Cloud Alibaba 的多模块工程骨架。

文档分工：本文件提供给项目成员（定位、快速开始、架构设计、中间件、端口）；完整设计思路与数据模型见 [`docs/architecture.md`](docs/architecture.md)。AI 工具的协作方式见下方「AI 协作开发指南」，规则入口见 [`AGENTS.md`](AGENTS.md)。

## 技术基线

版本以根 [`pom.xml`](pom.xml) 为准，AI 工具的技术栈约束见 [`AGENTS.md`](AGENTS.md) §2。当前基线：JDK 21、Spring Boot 3.5.16、Spring Cloud 2025.0.3、Spring Cloud Alibaba 2025.0.0.0、MyBatis-Plus 3.5.17、Redisson 3.52.0、RocketMQ 5.3.1、Nacos 3.0.3、MySQL 8.0、Redis 7、Vue 3 + Vite 8 + Element Plus 2.14。

## 模块结构

```
atifah/
├── pom.xml                        # 聚合工程 + 版本管理
├── atifah-common/              # 公共能力
│   ├── common-core                # Result / ErrorCode / BusinessException / 常量
│   ├── common-web                 # 统一异常、TraceId、Sentinel、Actuator
│   ├── common-redis               # RedisTemplate 序列化、缓存 Key、Redisson
│   ├── common-mybatis             # 分页插件、BaseEntity（SQL 走 Mapper XML）
│   ├── common-mq                  # Topic / 消费组常量、MessagePublisher
│   └── common-security            # JWT 签发校验、PasswordEncoder
├── atifah-gateway/             # Spring Cloud Gateway
├── atifah-services/            # C 端业务服务
│   ├── auth-service               # 8081 认证授权
│   ├── user-service               # 8082 用户与隐私设置
│   ├── mood-service               # 8083 打卡与情绪日记
│   ├── analysis-service           # 8084 趋势、量表与报告
│   ├── community-service          # 8085 树洞、社区与 Feed
│   ├── content-service            # 8086 冥想、文章与课程
│   ├── message-service            # 8087 站内信、WebSocket、推送
│   ├── growth-service             # 8088 签到、任务、积分
│   └── stats-service              # 8089 埋点与运营统计
├── atifah-admin-web/           # 管理后台
│   ├── frontend/                  # Vue 3 前端（Vite独立构建）
│   └── backend/                   # 8090 管理后台 BFF（Spring Boot）
├── atifah-app/                 # Flutter App（占位）
├── deploy/
│   ├── dev-deploy.md              # 开发环境部署说明（JVM / Docker 两套方案）
│   ├── docker-compose/            # 中间件 compose + 后端容器编排
│   ├── nacos/                     # Nacos 共享配置源文件 + 导入说明
│   ├── nginx/                     # 本地静态托管验证（可选）
│   └── sql/                       # 初始化 Schema
```

## 总体架构设计

完整设计目标、容量假设、核心链路、缓存/MQ/分片、部署与演进见 [`docs/architecture.md`](docs/architecture.md)；数据模型见 [`docs/db.md`](docs/db.md)，业务边界见 [`docs/domain.md`](docs/domain.md)。本文件不重复架构正文。

一句话概括：C 端 App + 管理后台 Vue 3 SPA，经网关进入 Spring Cloud Alibaba 微服务；同步查询走 OpenFeign，异步事件走 RocketMQ；MySQL 一服务一库，Redis 多级缓存，敏感数据按分级加密或脱敏；强一致范围最小化，优先最终一致 + 对账。

## 环境要求

- JDK 21+（确保 `JAVA_HOME` 指向 JDK 21，`java -version` 输出 21.x）
- Maven 3.9+
- Node.js `^20.19.0 || >=22.12.0`（管理后台前端，Vite 8 要求）
- Docker / Docker Compose（用于启动 MySQL、Redis、Nacos、RocketMQ 等中间件）
- IDE：IntelliJ IDEA（推荐）

> 仅编译 Maven 工程不需要 Docker；启动服务前需先准备中间件（本机 Docker 或自建实例）。

## 快速开始

> 完整的开发环境部署步骤见 [`deploy/dev-deploy.md`](deploy/dev-deploy.md)。

### 1. 启动中间件

```bash
docker compose -f deploy/docker-compose/docker-compose.yml up -d
```

启动后：

| 组件                  | 地址                      | 说明                                     |
| ------------------- | ----------------------- | -------------------------------------- |
| MySQL               | `127.0.0.1:3306`        | 用户 `root` / 密码 `root`，自动创建各服务 Schema   |
| Redis               | `127.0.0.1:6379`        | 无密码                                    |
| Nacos               | `127.0.0.1:8848`        | 注册与配置中心                                |
| Nacos Console       | <http://localhost:8849> | Nacos 3.x 控制台，宿主机映射端口                  |
| RocketMQ NameServer | `127.0.0.1:9876`        |                                        |
| RocketMQ Dashboard  | <http://localhost:8180> |                                        |
| Sentinel Dashboard  | <http://localhost:8858> | 默认账号/密码 `sentinel` / `sentinel`        |
| Seata（可选）           | `127.0.0.1:8091`        | `docker compose --profile seata up -d` |

### 2. 编译

```bash
mvn clean package -DskipTests
```

### 3. 启动后端服务

启动顺序建议：先 `atifah-gateway`，再按需启动业务服务。可在 IDEA 中直接运行各 `*ServiceApplication`，或使用：

```bash
java -jar atifah-gateway/target/atifah-gateway-1.0.0-SNAPSHOT.jar
java -jar atifah-services/user-service/target/user-service-1.0.0-SNAPSHOT.jar
java -jar atifah-admin-web/backend/target/admin-service-1.0.0-SNAPSHOT.jar
```

所有服务通过 Nacos 注册，网关按 `lb://atifah-<service>` 路由。

也可以把网关与业务服务全部跑在容器里（方案 B，需先完成上面的 `mvn package`）：

```bash
docker compose -f deploy/docker-compose/docker-compose.app.yml up -d --build
```

每个后端模块目录下还有独立的 `Dockerfile` 与 `docker-compose.yml`，可只构建/启动单个服务：

```bash
docker compose -f atifah-services/user-service/docker-compose.yml up -d --build
```

两套方案端口一致，二选一；镜像构建、单服务与统一编排、按需启动与排障见
[`deploy/dev-deploy.md`](deploy/dev-deploy.md) §6.2。

### 4. 启动管理后台前端

管理后台为独立的 Vue 工程，开发期通过 Vite 代理 `/admin/api` 到网关：

```bash
cd atifah-admin-web/frontend
npm install
npm run dev
```

访问 <http://localhost:5173>。前端环境变量见
[`atifah-admin-web/README.md`](atifah-admin-web/README.md)。

### 5. 健康检查

```bash
curl http://localhost:8080/actuator/health   # Gateway
curl http://localhost:8082/actuator/health   # user-service
curl http://localhost:8090/actuator/health   # admin-service（管理后台 BFF）
```

## 配置约定

- 配置优先级：环境变量 > Nacos（公共配置 `ATIFAH_PUBLIC_GROUP`，各服务私有配置在各自 group）> 本地 `application.yml`。
- 常用环境变量：

| 变量                                                            | 默认值                                    |
| ------------------------------------------------------------- | -------------------------------------- |
| `NACOS_ADDR`                                                  | `127.0.0.1:8848`                       |
| `NACOS_NAMESPACE`                                             | 默认 `atifah_dev`                       |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USER` / `MYSQL_PASSWORD` | `127.0.0.1` / `3306` / `root` / `root` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD`                | `127.0.0.1` / `6379` / 空               |
| `ROCKETMQ_NAMESRV`                                            | `127.0.0.1:9876`                       |
| `SENTINEL_DASHBOARD`                                          | `127.0.0.1:8858`                       |
| `JWT_SECRET`                                                  | 开发默认值（生产必须替换）                          |

- 方案 B（Docker）额外支持 `BASE_IMAGE`（基础镜像，默认 `eclipse-temurin:21-jre-jammy`）与
  `APP_MEM_LIMIT`（单容器内存上限，默认 `1g`），见 `deploy/dev-deploy.md` §6.2。
- 管理后台前端环境变量（`VITE_API_BASE_URL`、`VITE_API_PROXY_TARGET`）见 `atifah-admin-web/README.md`。
- Servlet 业务服务已开启虚拟线程（`spring.threads.virtual.enabled=true`）；`atifah-gateway` 为响应式 Netty 栈，不适用该配置。
- 网关 JWT 鉴权当前默认关闭（`atifah.gateway.auth.enabled=false`），接入 `auth-service` 签发 Token 后开启。
- 生产环境请将 `JWT_SECRET` 替换为至少 256 位的强随机密钥，或切换为 RS256 公私钥方案。
- `growth-service` 的 Seata 默认关闭（`seata.enabled=false`），需要分布式事务时再启用。

## 已知依赖说明

- 网关迁移至`spring-cloud-starter-gateway-server-webflux`（配置 key 同步迁移），启动不再有 deprecation / key 改名 WARN。
  4.3.x 内新 artifact 仍编译依赖经典 jar，属 Spring 官方过渡设计，SC 2025.1.x（gateway 5.0）起才彻底分离。
- `/actuator/gateway/**` 路由巡检端点在 SCG 4.3.5 上未注册（迁移前即如此，非迁移回归），路由验证用逐路径探测代替。
- 后续平台升级（SC 2025.1.x + SCA 2025.1.0.0）要求 **Spring Boot 4.0**，属跨大版本迁移（Framework 7、
  全模块回归，MyBatis-Plus/Redisson/RocketMQ 适配待核实），另立项目，不与本仓库日常迭代混做。

## 开发约定

硬约束见 [`AGENTS.md`](AGENTS.md) §4 与 [`docs/rules/`](docs/rules/)；本节不重复规则正文。管理后台请求链路：浏览器 → 网关 `/admin/api/**` → `admin-service`，前端不直连业务服务或数据库。

## AI 协作开发指南（Claude / CodeBuddy / Codex）

> 规则唯一入口是 [`AGENTS.md`](AGENTS.md)；本指南只给出入口与交接顺序，不重复规则正文。

| 角色 | 负责 | 规则入口 |
| --- | --- | --- |
| Claude（架构） | ADR、契约、任务包、公共能力、网关/部署/CI | `AGENTS.md` §0、`docs/contracts/`、`docs/tasks/` |
| CodeBuddy（开发） | 按 Feature 任务包实现与测试 | `AGENTS.md` §0、目标模块 `AGENTS.md`、任务包 |
| Codex（评审） | 只读评审 + 按风险核验 CI / 补测 | `AGENTS.md` §0、[`docs/review/standards.md`](docs/review/standards.md) |
| 人类（维护者） | 确认架构、合并、打基线、改 `done` | `AGENTS.md` §0 |

标准交接顺序：

1. Claude 产出 ADR + 契约 +（Feature）任务包；人工确认后在 `main` 打 `arch-baseline-vX.Y`。
2. Feature：CodeBuddy 从 `main` 拉 `feat/T-xxx-<slug>`，只改任务包允许路径，自检后开 PR；Arch/Ops/Docs 由 Claude 在 `arch/`、`ops/`、`docs/` 分支实现。
3. CI 通过后，Codex 核验 Reviewed SHA 对应的 CI，并按风险补跑 CI 未覆盖的测试；在 PR 评论记录结论和验证证据，报告文件可选。
4. `Blocker` / `Major` 或验证失败回实现者；架构/契约问题回 Claude。
5. 人类合并；维护者批量把 Feature 任务改为 `status: done`。

分支、提交、PR 与任务状态机的完整规则见 [`docs/rules/git-workflow.md`](docs/rules/git-workflow.md)、[`docs/tasks/README.md`](docs/tasks/README.md)；命令与自检见 `AGENTS.md` §9。

## 下一步

1. 补充各服务 Controller / Service / Mapper 与核心表 DDL（Flyway）。
2. 打通打卡链路（mood → RocketMQ → stats/analysis/growth/message）。
3. 接入 Sentinel 规则持久化、SkyWalking、Prometheus/Grafana。
4. 完善管理后台 Vue 页面与 `admin-service` 接口，接入独立鉴权与 MFA。
5. 迭代 Flutter App。
