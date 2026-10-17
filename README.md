# microhoov

情绪管理高并发微服务项目（App + 管理后台），基于 Spring Boot / Spring Cloud Alibaba 的多模块工程骨架。

完整设计见 [docs/architecture.md](docs/architecture.md)。

## 技术基线

| 组件 | 版本 |
|---|---|
| JDK | 17 |
| Spring Boot | 3.5.16 |
| Spring Cloud | 2025.0.3 |
| Spring Cloud Alibaba | 2025.0.0.0 |
| MyBatis-Plus | 3.5.17 |
| Redisson | 3.52.0 |
| RocketMQ Spring Starter | 2.3.6 |
| JJWT | 0.12.7 |
| MySQL | 8.0 |
| Redis | 7.x |
| RocketMQ | 5.3.1 |
| Nacos | 3.0.3 |

## 模块结构

```
microhoov/
├── pom.xml                        # 聚合工程 + 版本管理
├── microhoov-common/              # 公共能力
│   ├── common-core                # Result / ErrorCode / BusinessException / 常量
│   ├── common-web                 # 统一异常、TraceId、Sentinel、Actuator
│   ├── common-redis               # RedisTemplate 序列化、缓存 Key、Redisson
│   ├── common-mybatis             # 分页插件、审计字段填充、BaseEntity
│   ├── common-mq                  # Topic / 消费组常量、MessagePublisher
│   └── common-security            # JWT 签发校验、PasswordEncoder
├── microhoov-gateway/             # Spring Cloud Gateway
├── microhoov-services/            # 业务服务
│   ├── auth-service               # 8081 认证授权
│   ├── user-service               # 8082 用户与隐私设置
│   ├── mood-service               # 8083 打卡与情绪日记
│   ├── analysis-service           # 8084 趋势、量表与报告
│   ├── community-service          # 8085 树洞、社区与 Feed
│   ├── content-service            # 8086 冥想、文章与课程
│   ├── message-service            # 8087 站内信、WebSocket、推送
│   ├── growth-service             # 8088 签到、任务、积分
│   ├── stats-service              # 8089 埋点与运营统计
│   └── admin-service              # 8090 管理后台 BFF
├── microhoov-app/                 # Flutter App（占位）
├── microhoov-admin-web/           # Vue3 管理后台（占位）
├── deploy/
│   ├── docker-compose/            # 本地中间件一键启动
│   ├── k8s/                       # K8s 部署清单（占位）
│   └── sql/                       # 初始化 Schema
└── docs/architecture.md           # 架构设计文档
```

## 环境要求

- JDK 17+
- Maven 3.9+
- Docker / Docker Compose（用于启动 MySQL、Redis、Nacos、RocketMQ 等中间件）
- IDE：IntelliJ IDEA（推荐）

> 当前机器未检测到 Docker。仅编译 Maven 工程不需要 Docker；启动服务前需先准备中间件（本机安装或使用其他机器上的 Docker）。

## 快速开始

### 1. 启动中间件

```bash
docker compose -f deploy/docker-compose/docker-compose.yml up -d
```

启动后：

| 组件 | 地址 | 说明 |
|---|---|---|
| MySQL | `127.0.0.1:3306` | 用户 `root` / 密码 `root`，自动创建各服务 Schema |
| Redis | `127.0.0.1:6379` | 无密码 |
| Nacos | `127.0.0.1:8848` | 注册与配置中心 |
| Nacos Console | http://localhost:8849 | Nacos 3.x 控制台，宿主机映射端口 |
| RocketMQ NameServer | `127.0.0.1:9876` | |
| RocketMQ Dashboard | http://localhost:8180 | |
| Sentinel Dashboard | http://localhost:8858 | 默认账号/密码 `sentinel` / `sentinel` |
| Seata（可选） | `127.0.0.1:8091` | `docker compose --profile seata up -d` |

### 2. 编译

```bash
mvn clean package -DskipTests
```

### 3. 启动服务

启动顺序建议：先 `microhoov-gateway`，再按需启动业务服务。可在 IDEA 中直接运行各 `*ServiceApplication`，或使用：

```bash
java -jar microhoov-gateway/target/microhoov-gateway-1.0.0-SNAPSHOT.jar
java -jar microhoov-services/user-service/target/user-service-1.0.0-SNAPSHOT.jar
```

所有服务通过 Nacos 注册，网关按 `lb://microhoov-<service>` 路由。

### 4. 健康检查

```bash
curl http://localhost:8080/actuator/health   # Gateway
curl http://localhost:8082/actuator/health   # user-service
```

## 配置约定

- 配置优先级：环境变量 > Nacos（`MICROHOOV_GROUP`）> 本地 `application.yml`。
- 常用环境变量：

| 变量 | 默认值 |
|---|---|
| `NACOS_ADDR` | `127.0.0.1:8848` |
| `NACOS_NAMESPACE` | 空（public） |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USER` / `MYSQL_PASSWORD` | `127.0.0.1` / `3306` / `root` / `root` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `127.0.0.1` / `6379` / 空 |
| `ROCKETMQ_NAMESRV` | `127.0.0.1:9876` |
| `SENTINEL_DASHBOARD` | `127.0.0.1:8858` |
| `JWT_SECRET` | 开发默认值（生产必须替换） |

- 网关 JWT 鉴权当前默认关闭（`microhoov.gateway.auth.enabled=false`），接入 `auth-service` 签发 Token 后开启。
- 生产环境请将 `JWT_SECRET` 替换为至少 256 位的强随机密钥，或切换为 RS256 公私钥方案。
- `growth-service` 的 Seata 默认关闭（`seata.enabled=false`），需要分布式事务时再启用。

## 开发约定

- 服务名：`microhoov-<service>`；端口按上表分配。
- 每个服务独立 Schema，禁止跨库 JOIN。
- 跨服务同步查询用 OpenFeign，异步解耦用 RocketMQ。
- 对外接口统一返回 `Result<T>`，分页统一 `PageResult<T>`。
- 写接口使用 `Idempotency-Key` 请求头保证幂等。
- 敏感字段（情绪正文、量表结果）必须加密存储，日志禁止打印正文。

## 下一步

1. 补充各服务 Controller / Service / Mapper 与核心表 DDL（Flyway）。
2. 实现 auth-service 登录与 Token 签发，开启网关鉴权。
3. 打通打卡链路（mood → RocketMQ → stats/analysis/growth/message）。
4. 接入 Sentinel 规则持久化、SkyWalking、Prometheus/Grafana。
5. 搭建 Vue3 管理后台与 Flutter App。
