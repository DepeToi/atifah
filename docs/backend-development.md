# 后端开发规范（microhoov 服务端）

> 适用范围：`microhoov-common`、`microhoov-gateway`、`microhoov-services`、`microhoov-admin-web/backend`。
> 工程骨架与模块继承关系见根目录 `CODEBUDDY.md`，完整设计见 `docs/architecture.md`。
> 前后端接口统一规则见 [`docs/api-contract.md`](api-contract.md)。

## 1. 模块与包结构

- 包根：`com.microhoov.<service>`，例如 `com.microhoov.mood`、`com.microhoov.admin`、`com.microhoov.gateway`。
- 公共代码一律放 `com.microhoov.common.*`（core / web / redis / mybatis / mq / security），业务代码不得跨到公共包。
- 业务服务内按层分包：`controller` / `service` / `mapper` / `entity` / `dto` / `vo` / `config`。
- Mapper 包路径固定为 `com.microhoov.<service>.mapper`（启动类 `@MapperScan` 指向此处，见下）。

## 2. 服务启动类模板

每个业务服务只有一个启动类，保持最小化，不要在其中写配置：

```java
package com.microhoov.mood;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@MapperScan("com.microhoov.mood.mapper")
@SpringBootApplication
public class MoodServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoodServiceApplication.class, args);
    }
}
```

三个注解缺一不可：`@SpringBootApplication`、`@EnableDiscoveryClient`（Nacos 注册）、`@MapperScan("com.microhoov.<service>.mapper")`。

## 3. 统一响应与异常

- 对外接口统一返回 `Result<T>`（`com.microhoov.common.core.result.Result`），分页返回 `PageResult<T>`。
- `Result` 字段：`code` / `message` / `data` / `timestamp`；`code == 0` 表示成功，用 `Result.ok(data)`、`Result.fail(ErrorCode)`。
- `PageResult` 用静态工厂 `PageResult.of(records, total, page, size)` 构造。
- 错误码统一取 `ErrorCode` 枚举（40000 参数错误、40100 未认证、40300 无权限、40400 不存在、40900 冲突、42900 限流、50000 系统错误）。
- 业务校验失败**抛 `BusinessException`**（`com.microhoov.common.core.exception.BusinessException`），由 `GlobalExceptionHandler` 统一转成 `Result` 并映射 HTTP 状态码。不要在 Controller 里自己 try-catch 包装。
- 参数校验优先用 Jakarta Validation（`@Valid` + 注解），`MethodArgumentNotValidException` 与 `ConstraintViolationException` 已被全局处理。

```java
// 正确：抛业务异常
if (user == null) {
    throw new BusinessException(ErrorCode.NOT_FOUND);
}
return Result.ok(user);
```

## 4. 实体与 MyBatis-Plus

- 数据库实体继承 `com.microhoov.common.mybatis.entity.BaseEntity`，它已提供：
  - `id`：`@TableId(type = IdType.ASSIGN_ID)` 雪花 ID（不要自增主键）。
  - `createdAt` / `updatedAt`：`@TableField(fill = ...)`，由 `MetaObjectHandler` 自动填充，无需手动 set。
  - `deleted`：`@TableLogic` 逻辑删除（0 未删 / 1 已删）。
- 分页插件 `PaginationInnerInterceptor(MYSQL)` 已由 `MybatisPlusAutoConfiguration` 装配，分页直接用 MyBatis-Plus 的 `Page<T>`。
- 下划线字段映射驼峰、逻辑删除字段名 `deleted` 已在 Nacos `common-application.yaml` 配好，实体字段名与表列名保持一致即可。

```java
@Getter
@Setter
@TableName("t_mood_record")
public class MoodRecord extends BaseEntity {
    private Long userId;
    private String recordDate;
    // ... 业务字段
}
```

## 5. Redis

- 缓存 Key 必须统一登记到 `com.microhoov.common.redis.constant.CacheKeys`，禁止各服务散落硬编码 key，避免冲突。
- 已提供常用 key 模板：`CacheKeys.MOOD_TODAY`、`CacheKeys.IDEMPOTENT`、`CacheKeys.LOCK`、`CacheKeys.DETAIL` 等，用 `String.format` 填充。
- `RedisTemplate<String, Object>` 已由 `RedisAutoConfiguration` 装配（Key 用 String 序列化，Value 用 JSON），直接注入使用。
- 分布式锁用 Redisson（`common-redis` 已引入），锁 key 用 `CacheKeys.LOCK` 模板。

## 6. 消息队列（RocketMQ）

- Topic 统一登记到 `com.microhoov.common.mq.constant.MqTopics`，消费组登记到 `MqConsumerGroups`，不要硬编码字符串。
- 发消息统一用 `com.microhoov.common.mq.publisher.MessagePublisher`（`send` / `sendAsync`），不要直接拿 `RocketMQTemplate` 裸发。
- **key 用业务键（通常 userId）**，`MessagePublisher` 会把 key 写入 `RocketMQHeaders.KEYS`，同一 key 的事件可顺序消费。
- 消费端必须做幂等（msgId 去重 + 业务唯一键），失败走重试 + 死信队列。

```java
@Service
@RequiredArgsConstructor
public class MoodRecordService {
    private final MessagePublisher messagePublisher;

    public void publishRecorded(Long userId, Object payload) {
        messagePublisher.sendAsync(MqTopics.MOOD_RECORDED, String.valueOf(userId), payload);
    }
}
```

## 7. 配置规范

- 配置优先级：**环境变量 > Nacos > 本地 `application.yml`**。
- Nacos 分组固定 `MICROHOOV_GROUP`；业务服务通过 `spring.config.import` 引入两条：`<服务名>.yaml` 与 `common-application.yaml`（均为 `optional:`）。
- 共享配置（虚拟线程、数据源、Redis、Jackson、MyBatis-Plus、RocketMQ、JWT 默认值等）放 `deploy/nacos/config/common-application.yaml`；**差异化项保留在本地 `application.yml`**：端口、应用名、数据源 URL、生产者组。
- 连接类配置用环境变量占位（`${MYSQL_HOST:127.0.0.1}` 等），不要写死。

本地 `application.yml` 的最小结构（以 mood-service 为例）：

```yaml
server:
  port: 8083
  shutdown: graceful

spring:
  application:
    name: microhoov-mood
  config:
    import:
      - "optional:nacos:${spring.application.name}.yaml?group=MICROHOOV_GROUP&refreshEnabled=true"
      - "optional:nacos:common-application.yaml?group=MICROHOOV_GROUP&refreshEnabled=true"
  cloud:
    nacos:
      server-addr: ${NACOS_ADDR:127.0.0.1:8848}
      discovery:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:}
        group: MICROHOOV_GROUP
      config:
        server-addr: ${NACOS_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:}
        group: MICROHOOV_GROUP
        file-extension: yaml
  datasource:
    url: "jdbc:mysql://${MYSQL_HOST:127.0.0.1}:${MYSQL_PORT:3306}/microhoov_mood?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}

rocketmq:
  producer:
    group: microhoov-mood-producer
```

## 8. 网关

- 路由集中配在 `microhoov-gateway/src/main/resources/application.yml`，按路径前缀映射 `lb://microhoov-<service>`。
- C 端接口走 `/api/v1/**`，管理端走 `/admin/api/**`，WebSocket 走 `/ws/**`。新增服务时在路由表补一条。
- 网关已有两个全局过滤器：`TraceIdGlobalFilter`（生成/透传 TraceId）与 `AuthGlobalFilter`（JWT 鉴权）。
- `AuthGlobalFilter` 由 `microhoov.gateway.auth.enabled` 控制，**默认关闭**；开启后校验 Bearer Token 并把 `X-User-Id` / `X-Username` 写入下游请求头。白名单在 `microhoov.gateway.auth.whitelist`。

## 9. 日志与 TraceId

- TraceId 由网关或 `common-web` 的 `TraceIdFilter` 生成，写入 MDC（key 为 `traceId`）并回传 `X-Trace-Id` 响应头。
- 日志格式已带 trace 占位：`%5p [%X{traceId:-}]`，直接使用 `log.info(...)` 即可自动带上 TraceId。
- **敏感字段（日记正文、量表结果、手机号、密码）严禁打印**，日志只打 ID / 状态 / 脱敏摘要。
- 用 Lombok `@Slf4j` 注入 logger。

## 10. 幂等与安全

- 写接口要求客户端携带 `Idempotency-Key` 请求头（常量 `CommonConstants.HEADER_IDEMPOTENCY_KEY`），服务端用 `CacheKeys.IDEMPOTENT` 做 Redis 去重 + 数据库唯一索引双保险。
- 敏感字段（情绪正文、量表结果）必须字段级加密存储（AES-256-GCM），密钥由 KMS/Vault 管理。
- JWT 签发/校验统一走 `com.microhoov.common.security.jwt.JwtTokenProvider`；`PasswordEncoder` 用已装配的 `BCryptPasswordEncoder`。
- 跨服务身份传递：网关已把用户身份写入 `X-User-Id` / `X-Username`，下游服务读 `CommonConstants.HEADER_USER_ID` / `HEADER_USERNAME`。

## 11. 代码风格

- 统一用 Lombok 精简样板：`@Getter` / `@Setter` / `@RequiredArgsConstructor`（构造注入） / `@Slf4j`。
- 依赖注入用**构造器注入**（`final` 字段 + `@RequiredArgsConstructor`），不用 `@Autowired` 字段注入。
- 可序列化 DTO/实体带 `@Serial private static final long serialVersionUID = 1L;`。
- 类注释与关键方法注释用中文 Javadoc，与现有代码一致。
- Java 21 语法可用（switch 表达式、record 等），JDK 与编译器 release 均为 21。

## 12. 命名与约定速查

| 项 | 约定 |
| --- | --- |
| 服务名 | `microhoov-<service>`（如 microhoov-mood） |
| 端口 | 网关 8080，auth 8081，user 8082，mood 8083，analysis 8084，community 8085，content 8086，message 8087，growth 8088，stats 8089，admin 8090 |
| 数据库 Schema | `microhoov_<service>`，一服务一库，禁止跨库 JOIN |
| RocketMQ 生产者组 | `microhoov-<service>-producer` |
| RocketMQ 消费组 | 登记在 `MqConsumerGroups`（如 `microhoov-stats-mood-consumer`） |
| 请求头 | `X-Trace-Id` / `X-User-Id` / `X-Username` / `Idempotency-Key`（见 `CommonConstants`） |

## 13. 骨架阶段约定与下一步

当前各业务服务只有启动类，Controller/Service/Mapper 尚未实现。新增业务代码时遵循本规范：

1. 先补实体（继承 `BaseEntity`）+ Mapper（接口继承 `BaseMapper<T>`）。
2. Service 用 `@RequiredArgsConstructor` 构造注入 Mapper / Redis / MessagePublisher。
3. Controller 返回 `Result<T>`，参数校验用 `@Valid`，业务失败抛 `BusinessException`。
4. 涉及缓存 / MQ 时，先到 `CacheKeys` / `MqTopics` / `MqConsumerGroups` 登记，再引用。
5. 实现 auth-service 登录与 Token 签发后，开启网关鉴权（`microhoov.gateway.auth.enabled=true`）。
