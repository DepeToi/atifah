# CODEBUDDY.md

This file provides guidance to CodeBuddy Code when working with code in this repository.

## 项目概览

microhoov 是一个情绪管理微服务项目（C 端 App + 管理后台），当前处于**骨架/骨架**阶段——大部分业务服务只有 `*ServiceApplication` 启动类，Controller/Service/Mapper 尚未实现。完整设计见 `docs/architecture.md`（业务域、容量假设、缓存/MQ/分库分表方案）。根目录 `README.md` 是技术基线、模块布局与快速开始的权威来源。前后端接口统一规则见 [`docs/api-contract.md`](docs/api-contract.md)。

技术栈：JDK 21 · Spring Boot 3.5.16 · Spring Cloud 2025.0.3 · Spring Cloud Alibaba 2025.0.0.0 · MyBatis-Plus 3.5.17 · Redisson · RocketMQ Spring Starter 2.3.6 · JJWT · Vue 3 + Vite + Element Plus（管理后台前端）。

## 常用命令

除特别说明外，均在仓库根目录执行。需要 JDK 21（`JAVA_HOME` → 21）和 Maven 3.9+。

~~~bash
# 全量构建（跳过测试）
mvn clean package -DskipTests

# 构建单个模块及其上游依赖（管理后台后端不在 services 反应堆链路中）
mvn -pl microhoov-admin-web/backend -am package -DskipTests

# 运行测试（目前没有测试；下面是后续可用的单测形式）
mvn test
mvn -pl microhoov-common/common-core test -Dtest=SomeTest#someMethod

# 启动本地中间件（MySQL/Redis/Nacos/RocketMQ/Sentinel）——需要 Docker
docker compose -f deploy/docker-compose/docker-compose.yml up -d

# 运行服务（或直接在 IDEA 里运行各 *ServiceApplication）
java -jar microhoov-gateway/target/microhoov-gateway-1.0.0-SNAPSHOT.jar
java -jar microhoov-services/user-service/target/user-service-1.0.0-SNAPSHOT.jar
java -jar microhoov-admin-web/backend/target/admin-service-1.0.0-SNAPSHOT.jar

# 无 Nacos 单机运行（关闭注册与配置中心）
java -jar <module>/target/<artifact>-1.0.0-SNAPSHOT.jar \
  --spring.cloud.nacos.discovery.enabled=false \
  --spring.cloud.nacos.config.enabled=false

# 管理后台前端（独立构建，不属于 Maven）
cd microhoov-admin-web/frontend
npm install
npm run dev            # Vite 开发服务器，端口 :5173，代理 /admin/api -> 网关 :8080
npm run type-check     # 仅 vue-tsc 类型检查
npm run build          # 类型检查 + 构建 -> dist/
~~~

## 架构

### Maven 模块布局

三层反应堆。根 `pom.xml` 聚合四个模块：`microhoov-common`、`microhoov-gateway`、`microhoov-services`、`microhoov-admin-web/backend`。

~~~text
pom.xml (parent: spring-boot-starter-parent 3.5.16；只做 BOM + dependencyManagement)
├── microhoov-common/            (pom，无依赖——只聚合子模块)
│   ├── common-core              Result / ErrorCode / BusinessException / 常量
│   ├── common-web               全局异常、TraceId、Sentinel、Actuator
│   ├── common-redis             RedisTemplate 序列化、缓存 Key、Redisson
│   ├── common-mybatis           分页、审计字段填充、BaseEntity
│   ├── common-mq                Topic/消费组常量、MessagePublisher
│   └── common-security          JwtTokenProvider、JwtProperties、PasswordEncoder
├── microhoov-gateway/           Spring Cloud Gateway（响应式 Netty），端口 8080
├── microhoov-services/          (pom——引入所有 common 模块 + Nacos + OpenFeign + LoadBalancer + boot 插件)
│   ├── auth-service 8081 · user-service 8082 · mood-service 8083
│   ├── analysis-service 8084 · community-service 8085 · content-service 8086
│   ├── message-service 8087 (WebSocket) · growth-service 8088 · stats-service 8089
└── microhoov-admin-web/
    ├── frontend/                Vue 3 + Vite SPA（独立 npm 构建）
    └── backend/                 admin-service（BFF），端口 8090，artifactId 为 admin-service
~~~

关键的继承细节（单看一个文件看不出来）：

- **`microhoov-services/pom.xml`** 是所有业务服务的实际父 POM。它声明了五个 common 模块、`spring-cloud-starter-alibaba-nacos-discovery/config`、`spring-cloud-starter-openfeign`、`spring-cloud-starter-loadbalancer` 以及 `spring-boot-maven-plugin`。其下任何服务都会自动继承这些——不要再逐个服务重复添加 common 依赖。
- **`microhoov-admin-web/backend`** 通过 `<relativePath>../../microhoov-services/pom.xml</relativePath>` 复用同一个父 POM，但它直接聚合在根 pom 下（不是 `microhoov-services` 的子模块）。它的 artifactId 是 `admin-service`，不是目录名。
- **`microhoov-gateway`** 是独立的——它不继承 `microhoov-services`，只用了 `common-core` + `common-security`（JWT），运行响应式栈（没有 `common-web`/`common-mybatis`，不启用虚拟线程）。

### 公共模块通过 Spring Boot 自动装配生效

公共模块通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册自身（例如 `com.microhoov.common.web.config.CommonWebAutoConfiguration`）。服务**不需要** `@ComponentScan` 公共包——只要依赖在 classpath 上，能力就自动出现。`CommonWebAutoConfiguration` 标注了 `@ConditionalOnWebApplication(SERVLET)`，所以它只对业务服务生效，对响应式网关不生效。

### 配置分层

优先级：**环境变量 > Nacos > 本地 `application.yml`**。所有服务启动时都会导入 Nacos 配置：

~~~yaml
spring.config.import:
  - "optional:nacos:${spring.application.name}.yaml?group=MICROHOOV_GROUP&refreshEnabled=true"
  - "optional:nacos:common-application.yaml?group=MICROHOOV_GROUP&refreshEnabled=true"  # 仅业务服务
~~~

- `common-application.yaml`（源文件在 `deploy/nacos/config/`）承载共享配置：虚拟线程（`spring.threads.virtual.enabled=true`）、HikariCP、Redis、Jackson、Sentinel、OpenFeign 超时、MyBatis-Plus（雪花 ID `assign_id`、逻辑删除字段 `deleted`）、RocketMQ、Actuator、JWT 默认值、日志 trace 格式。它刻意排除了 Nacos 连接配置和各服务差异项（端口、应用名、数据源 URL、生产者组），这些保留在各服务本地 `application.yml`。
- Nacos 分组固定为 `MICROHOOV_GROUP`；命名空间默认 public。
- 本地配置是 `optional:`——即使 Nacos 挂了，服务也能用本地默认值启动。

### 服务约定

- 服务名：`microhoov-<service>`；网关按路径路由 `lb://microhoov-<service>`（C 端走 `/api/v1/**`，管理端/WebSocket 走 `/admin/api/**` 和 `/ws/**`）。路由表见 `microhoov-gateway/src/main/resources/application.yml`。
- RocketMQ 生产者组：`microhoov-<service>-producer`。
- 每个服务一个独立逻辑库（`microhoov_<service>`），由 `deploy/sql/01-init-databases.sql` 创建；禁止跨库 JOIN。
- 统一响应：`Result<T>`（`{code,message,data,timestamp}`，`code=0` 表示成功）和 `PageResult<T>`；错误码来自 `ErrorCode` 枚举。共享 API 类位于 `com.microhoov.common.core.*`。
- 写接口使用 `Idempotency-Key` 请求头；敏感字段（日记正文、量表结果）必须加密存储，日志禁止打印。

### 网关鉴权

`AuthGlobalFilter` 校验 JWT（HS256，`JwtTokenProvider`）并把 `X-User-Id` / `X-Username` 写入下游请求头。它由 `@ConditionalOnProperty("microhoov.gateway.auth.enabled")` 控制，**默认关闭**——待 `auth-service` 签发 Token 后再开启。白名单和 JWT 密钥在网关配置的 `microhoov.gateway.auth.*` / `microhoov.security.jwt.*` 下。

## 关键注意点

- Spring Cloud Gateway 4.3.x 已弃用 `spring-cloud-starter-gateway`，推荐迁移到 `spring-cloud-starter-gateway-server-webflux`；但 SCA 2025.0.0.0 的 Sentinel 网关适配器仍依赖经典 starter——暂时保留，启动时会有一条 deprecation WARN，属正常现象。
- 业务服务运行虚拟线程；网关（响应式）不能启用该开关。
- `JWT_SECRET` 是开发默认值（`microhoov-dev-secret-change-me-please-32bytes`）——生产环境请替换为 ≥256 位的密钥。
- `growth-service` 的 Seata 默认关闭（`seata.enabled=false`）。
