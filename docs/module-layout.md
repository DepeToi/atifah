# 模块布局与运行机制

> 读这份文档的时机：新增模块、调整依赖、排查「服务起不来 / 依赖注入不到 / 配置没生效」。
> 日常写业务代码不需要读，先看根目录 [`AGENTS.md`](../AGENTS.md)。
> 业务与容量设计见 [`architecture.md`](architecture.md)；数据模型见 [`db.md`](db.md)；排查清单见 [`pitfalls.md`](pitfalls.md)。

## 1. Maven 三层反应堆

根 `pom.xml` 只做两件事：继承 `spring-boot-starter-parent` 3.5.16，统一 `dependencyManagement`（Spring Cloud / SCA / MyBatis-Plus / Redisson / RocketMQ / JJWT）。它聚合四个模块：

```text
pom.xml (packaging=pom)
├── atifah-common/            packaging=pom，无自身依赖，只聚合 6 个子模块
│   ├── common-core              Result / PageResult / ErrorCode / BusinessException / CommonConstants
│   ├── common-web               GlobalExceptionHandler / TraceIdFilter / Sentinel / Actuator
│   ├── common-redis             RedisTemplate 序列化 / CacheKeys / Redisson
│   ├── common-mybatis           PaginationInnerInterceptor / MetaObjectHandler / BaseEntity / Flyway
│   ├── common-mq                MqTopics / MqConsumerGroups / MessagePublisher
│   └── common-security          JwtTokenProvider / JwtProperties / BCryptPasswordEncoder
├── atifah-gateway/           独立，响应式
├── atifah-services/          packaging=pom，9 个业务服务的实际父 POM
│   ├── auth-service 8081 · user-service 8082 · mood-service 8083
│   ├── analysis-service 8084 · community-service 8085 · content-service 8086
│   ├── message-service 8087（含 WebSocket）· growth-service 8088 · stats-service 8089
└── atifah-admin-web/
    ├── frontend/                Vue 3 SPA，独立 npm 构建，不参与 Maven
    └── backend/                 artifactId = admin-service，8090
```

### 三条容易踩的继承关系

1. **`atifah-services/pom.xml` 是业务服务的真实父 POM**。它已声明 `common-web` / `common-mybatis` / `common-redis` / `common-mq` / `common-security`、Nacos discovery + config、OpenFeign、LoadBalancer 以及 `spring-boot-maven-plugin`。在 `mood-service` 等子模块里再写一遍 common 依赖属于重复声明，评审直接打回。
2. **`admin-web/backend` 复用同一个父 POM 但不属于 `atifah-services`**：它通过 `<relativePath>../../atifah-services/pom.xml</relativePath>` + 同版本继承依赖，模块聚合位置却在根 pom。artifactId 是 `admin-service`，所以 jar 名是 `admin-service-1.0.0-SNAPSHOT.jar`。
3. **`atifah-gateway` 完全不继承上述两处**：只依赖 `common-core` + `common-security`，运行在响应式 Netty 上。因此它没有 `GlobalExceptionHandler`、没有 MyBatis、没有虚拟线程；往网关加 `common-web` 会引入 servlet 栈并导致启动失败。

## 2. 公共能力靠自动装配，不靠包扫描

每个 common 模块在 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 里登记自己的配置类，例如：

```text
com.atifah.common.web.config.CommonWebAutoConfiguration
com.atifah.common.mybatis.config.MybatisPlusAutoConfiguration
com.atifah.common.redis.config.RedisAutoConfiguration
com.atifah.common.mq.config.MqAutoConfiguration
com.atifah.common.security.config.CommonSecurityAutoConfiguration
```

含义：只要依赖在 classpath 上，能力就自动出现，业务服务**不需要** `@ComponentScan("com.atifah")`。反过来，新增公共能力时也应该走自动装配，而不是要求各服务扫包。

`CommonWebAutoConfiguration` 标注了 `@ConditionalOnWebApplication(SERVLET)`，所以它对响应式网关自动失效——这也是网关可以安全依赖 `common-security`、却拿不到 Web 层能力的原因。

## 3. 配置分层

优先级：**环境变量 > Nacos（group `atifah_GROUP`）> 本地 `application.yml`**。

业务服务启动时导入两条 Nacos 配置（均为 `optional:`，Nacos 挂了也能用本地默认值起来）：

```yaml
spring.config.import:
  - "optional:nacos:${spring.application.name}.yaml?group=atifah_GROUP&refreshEnabled=true"
  - "optional:nacos:common-application.yaml?group=atifah_GROUP&refreshEnabled=true"
```

职责划分（改配置前先确认归属，避免两处都改）：

| 配置 | 放哪里 | 内容 |
| --- | --- | --- |
| 共享配置 | `deploy/nacos/config/common-application.yaml`（需手动导入 Nacos） | 虚拟线程、HikariCP、Redis、Jackson、Sentinel、OpenFeign 超时、MyBatis-Plus（`assign_id`、逻辑删除 `deleted`）、RocketMQ、Actuator、JWT 默认值、日志 trace 格式 |
| 差异化配置 | 各服务本地 `application.yml` | 端口、`spring.application.name`、数据源 URL、RocketMQ 生产者组 |
| 连接参数 | 环境变量占位 | `NACOS_ADDR` / `MYSQL_*` / `REDIS_*` / `ROCKETMQ_NAMESRV` / `SENTINEL_DASHBOARD` / `JWT_SECRET` |

`common-application.yaml` 刻意不含 `spring.cloud.nacos.*`——连接 Nacos 所需的配置不可能来自 Nacos 自己，只能留在本地。

本地 `application.yml` 的最小结构（以 `mood-service` 为例）：

```yaml
server:
  port: 8083
  shutdown: graceful

spring:
  application:
    name: atifah-mood
  config:
    import:
      - "optional:nacos:${spring.application.name}.yaml?group=atifah_GROUP&refreshEnabled=true"
      - "optional:nacos:common-application.yaml?group=atifah_GROUP&refreshEnabled=true"
  cloud:
    nacos:
      server-addr: ${NACOS_ADDR:127.0.0.1:8848}
      discovery:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:}
        group: atifah_GROUP
      config:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:}
        group: atifah_GROUP
        file-extension: yaml
  datasource:
    url: "jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/atifah_mood?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}

rocketmq:
  producer:
    group: atifah-mood-producer
```

## 4. 网关与鉴权

- 路由集中配在 `atifah-gateway/src/main/resources/application.yml`，按前缀映射 `lb://atifah-<service>`；新增服务必须补一条路由，否则 404。
- 两个全局过滤器：`TraceIdGlobalFilter`（`HIGHEST_PRECEDENCE`，生成/透传 `X-Trace-Id` 并回写响应头）、`AuthGlobalFilter`（`-100`，校验 JWT 后注入 `X-User-Id` / `X-Username`）。
- `AuthGlobalFilter` 由 `atifah.gateway.auth.enabled` 控制，**当前默认 `false`**。开启前必须先把白名单补全：现状只有 `/api/v1/auth/` 与 `/actuator/`，`/ws/**`、`/admin/api/**` 会被直接 401。
- CORS 由网关统一处理，`CORS_ALLOWED_ORIGINS` 默认 `*`，生产必须收敛为具体域名。

## 5. 已知约束

> 更多「服务起不来 / 404 / 401 / 配置不生效」的排查见 [`pitfalls.md`](pitfalls.md)。

- Spring Cloud Gateway 4.3.x 已弃用 `spring-cloud-starter-gateway`，推荐 `spring-cloud-starter-gateway-server-webflux`；但 SCA 2025.0.0.0 的 Sentinel 网关适配器仍依赖经典 starter，暂时保留，启动时会有一条 deprecation WARN，属正常现象。
- 业务服务（servlet）启用虚拟线程，网关不启用。
- `common-mybatis` 引入了 Flyway，但仓库尚无 `db/migration` 脚本，启动时不会建表；补 DDL 时按服务放 `src/main/resources/db/migration/V1__init.sql`。
- `growth-service` 的 Seata 默认关闭（`seata.enabled=false`）。
