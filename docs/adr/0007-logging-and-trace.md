# 0007. 运行日志与链路关联方案

- 状态：已采纳
- 日期：2026-11-16
- 决策人：人类维护者

## 背景

当前系统只有 `X-Trace-Id` 的最小实现：网关 `TraceIdGlobalFilter` 生成/透传请求头，业务服务 `common-web` 的 `TraceIdFilter` 写入 MDC。存在以下缺口：

- TraceId 不校验格式，超长或含换行的请求头会进入日志，存在日志注入风险。
- 网关为响应式栈，未把 TraceId 写入 MDC；出问题后无法按一次请求检索全部网关日志。
- 网关和业务服务都没有访问日志，只有异常日志，无法回答“谁在什么时候调用了哪个接口、结果如何、耗时多少”。
- 客户端可伪造 `X-User-Id` / `X-Username`，网关未剥离，日志中的用户身份不可信。
- OpenFeign 未透传 TraceId 与用户身份，跨服务链路断开。
- RocketMQ 消息未携带 TraceId，生产与消费日志无法关联。
- `docs/architecture.md` 写了“JSON 结构化输出到 stdout → Loki”，但配置中只有 `logging.pattern.level`，未真正启用。
- SQL、Redis、RocketMQ、Nacos 等中间件日志级别未定义，生产环境噪声与敏感数据风险不可控。

情绪日记、量表结果、手机号、密码属于敏感数据，日志方案必须默认不采集正文、请求体、查询串、Token。运行日志也不能替代管理端审计日志（SEC-01）。

本变更由维护者确认按**单个 Arch PR** 实施，`SCOPE-02` 的 400 行限制人工豁免；仍遵守“一个 PR 只做一件事”的语义边界。

## 决策

### 1. TraceId 与 MDC

- `X-Trace-Id` 只接受 32 位十六进制（大小写兼容，统一转小写）；缺失或非法时由网关重新生成。
- 网关把 TraceId 写入 exchange 属性、下游请求头和响应头；业务服务复用合法值，写入 MDC `traceId`，并在 `finally` 清理。
- 业务服务从 `X-User-Id` 读取用户 ID，校验为 `[A-Za-z0-9_-]{1,64}` 后写入 MDC `userId`；非法值忽略。
- 网关**始终剥离**客户端传入的 `X-User-Id` / `X-Username`，只有鉴权通过后才写入下游；业务服务不得直接信任外部来源的身份头。
- 网关为响应式栈，不使用全局 Reactor MDC Hook；仅在访问日志/鉴权失败日志的同步写日志语句外层使用 `MDC.putCloseable`，避免事件循环线程串号。
- 业务服务当前均为同步请求，虚拟线程下 MDC 正常；后续如引入 `@Async` / `CompletableFuture`，需显式传播 MDC。
- SkyWalking / Micrometer Tracing 仍为后续可选项；启用前另立 ADR，不与本次轻量 TraceId 混用。

### 2. 访问日志

- 网关和每个业务服务各输出**一请求一行**访问日志，使用同一个 TraceId。
- 字段：`event`（网关 `gateway_access`，业务服务 `access`）、`method`、`path`（不含 query）、`status`、`durationMs`、`userId`；网关额外记录 `routeId` 与 `clientIp`（网关看到的客户端/代理地址，可配置关闭）；业务服务记录 `remoteIp`（通常是网关地址，可配置关闭），避免把网关地址误标成客户端 IP。
- 不记录请求体、响应体、URL query、Header、Cookie、Authorization、Token。
- 默认跳过 `/actuator/**`；业务服务额外跳过 `/error`。路径前缀可配置。
- 级别：2xx/3xx/4xx 用 `INFO`，5xx 用 `ERROR`；访问日志不带异常堆栈，异常堆栈由 `GlobalExceptionHandler` 或网关错误日志承担。
- 鉴权失败由网关额外记录 `event=auth_failed`（原因 `missing_token` / `invalid_token`），不记录 Token 与 claims。

### 3. 输出格式

- 使用 Spring Boot 3.5 原生结构化日志，不引入 `logstash-logback-encoder` 或其他日志依赖。
- 本地默认文本 pattern，输出到控制台。
- 生产设置环境变量 `ATIFAH_LOG_FORMAT=logstash`，启用 JSON 输出：
  - `@timestamp`、`message`、`logger_name`、`thread_name`、`level`、`level_value`、`stack_trace`
  - `service`（`spring.application.name`）、`env`（`APP_ENV`，默认 `local`）
  - MDC 中的 `traceId`、`userId`
- 应用只写 stdout，不配置 file appender；落盘、轮转、保留交给容器与 Loki。
- 不使用 Spring profile 文档控制日志格式：SCA Nacos ConfigData 对 profile 文档支持不可靠，改用 `ATIFAH_LOG_FORMAT` 环境变量，避免“配置写了但不生效”。
- 生产建议保留：访问日志 30 天、错误日志 90 天；`clientIp` 属于个人信息，可按合规要求关闭。

### 4. 链路透传

- OpenFeign：`common-web` 提供 `FeignTraceIdInterceptor`，透传 `X-Trace-Id`、`X-User-Id`、`X-Username`；不转发 `Authorization`、`Idempotency-Key`。
  - `common-web` 增加 `io.github.openfeign:feign-core` 的 `optional` 编译依赖，版本由现有 Spring Cloud BOM 管理；业务服务父 POM 已引入 OpenFeign，不新增实际运行依赖。
- RocketMQ：`MessagePublisher` 从 MDC 读取 TraceId，写入消息属性 `X-Trace-Id`；消息体不变。消费端从消息 header 取出并写入 MDC，`finally` 清理；日志只记录 `msgId/topic/tag/key`，不打印 payload。
- 定时任务暂不引入 TraceId；后续出现时按“每次执行生成一个 TraceId”处理。

### 5. 中间件日志级别

| 组件 | logger | 默认级别 | 排障方式 |
| --- | --- | --- | --- |
| MyBatis SQL | `com.atifah.<service>.mapper` | INFO | 本地用环境变量临时开 DEBUG，禁止共享/生产 |
| Redisson | `org.redisson` | WARN | 需要时临时覆盖为 INFO |
| RocketMQ 客户端 | `org.apache.rocketmq` | WARN | 需要时临时覆盖为 INFO |
| Nacos 客户端 | `com.alibaba.nacos` | WARN | 需要时临时覆盖为 INFO |
| Sentinel | `com.alibaba.csp.sentinel` | WARN | 需要时临时覆盖为 INFO |
| Seata | `io.seata` | WARN | 需要时临时覆盖为 INFO |
| HikariCP | `com.zaxxer.hikari` | INFO | 连接池启动信息保留 |
| Flyway | `org.flywaydb` | INFO | 迁移结果必须可见 |
| Reactor Netty | `reactor.netty` | WARN（仅网关） | 需要时临时覆盖为 INFO |

- 生产禁止 Mapper DEBUG、Feign `FULL/BASIC` 日志、MQ payload、Redis value。
- 慢 SQL 由 MySQL 慢查询日志/监控承担，不进入应用日志。
- 级别覆盖优先使用环境变量（如 `LOGGING_LEVEL_ORG_REDISSON=info`），不把排障值写进共享 Nacos。

### 6. 安全与审计

- 禁止进入日志：日记正文、量表结果、手机号、密码/哈希、Token、验证码、请求/响应体、URL query、MQ payload、Redis value、生产 SQL 参数。
- TraceId 与用户 ID 先校验再入 MDC，防日志注入。
- 运行日志不是审计日志；管理端查看明文、匿名帖反查走独立审计表与接口，单独出 ADR/契约。
- 一旦发现敏感明文进入日志，按安全事件处理：清理日志流、评估密钥轮换、补回归测试。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| A. Boot 原生结构化日志 + 自建访问日志 + 轻量 TraceId | 零新增依赖；字段可控；与现有 `X-Trace-Id` 兼容 | 需要自己维护访问日志与传播逻辑 | 采纳 |
| B. `logstash-logback-encoder` | 生态成熟，字段丰富 | 新增依赖；与 Boot 3.5 原生 JSON 能力重复；违反 DEPS 先确认原则 | 否决 |
| C. 立即引入 Micrometer Tracing / OpenTelemetry / SkyWalking | 完整 span、跨线程自动传播 | 新增依赖与 Agent；与规划中的 P4 可观测性重叠；需要单独 ADR | 否决，后续另立 |
| D. 只依赖 Nginx/Tomcat access log | 配置简单 | 没有 userId、routeId、服务内 MDC；无法按业务链路检索；多一层日志系统 | 否决 |

## 影响

- 正面：
  - 一次请求在网关、业务服务、Feign、MQ 消费端可用同一 TraceId 串联。
  - 访问日志字段稳定，便于 Loki 检索、告警与排障。
  - 不新增日志依赖，不改变现有 API 响应结构。
  - 明确中间件日志纪律，降低敏感数据与日志噪声风险。
- 负面/代价：
  - 每请求多一行访问日志，网关与业务服务日志量上升；后续如压力大可加确定性采样（错误不采样）。
  - `clientIp` 属于个人信息，需要按环境配置开关与保留期；业务服务的 `remoteIp` 可单独关闭。
  - 网关剥离客户端身份头后，手工 curl 不能再伪造 `X-User-Id`，本地联调需走鉴权或直接调用业务服务。
  - 生产默认不打印 SQL 参数，SQL 排障需使用本地 DEBUG 或数据库慢查询日志。
- 需要同步修改的文档与代码：
  - `docs/architecture.md` §9
  - `docs/contracts/api-conventions.md` §5/§6
  - `docs/rules/code-style.md` CODE-07
  - `docs/contracts/events/_template.md`
  - `atifah-common/common-core`、`common-web`、`common-mq`
  - `atifah-gateway` 过滤器与本地 `application.yml`
  - `deploy/nacos/config/common-application.yaml`
- 回滚方式：
  - 关闭访问日志：`atifah.logging.access.enabled=false`。
  - 关闭 JSON：移除 `ATIFAH_LOG_FORMAT` 环境变量。
  - 代码回滚不影响数据库、表结构与契约响应；无数据迁移。
- 上线注意：
  - 重新发布 `deploy/nacos/config/common-application.yaml` 到 Nacos 的 `ATIFAH_PUBLIC_GROUP`。
  - 网关不导入公共 Nacos 配置，其日志配置在 `atifah-gateway/src/main/resources/application.yml`。
  - 生产设置 `ATIFAH_LOG_FORMAT=logstash`、`APP_ENV=prod`；时区建议统一 `TZ=Asia/Shanghai` 或全链路 UTC。
  - 修改 `logging.level.*` 或 `ATIFAH_LOG_FORMAT` 后需重启服务生效，不依赖运行中热更新。

## 关联

- 相关契约：`docs/contracts/api-conventions.md` §5/§6、`docs/contracts/events/_template.md`
- 相关规则：`docs/rules/code-style.md` CODE-07、`docs/rules/security.md` SEC-01/§SEC-03
- 相关任务包：无（Arch 变更，按 ADR + 契约/代码 diff 交付）
- 替代/被替代的 ADR：无
