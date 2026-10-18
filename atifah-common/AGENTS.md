# AGENTS.md — atifah-common

> 模块级规则，与根 [`AGENTS.md`](../AGENTS.md) 及 [`.claude/rules/*`](../.claude/rules/) 叠加生效；冲突时以本文件为准（就近覆盖）。

## 定位

- 本模块只放**跨服务公共能力**：`core` / `web` / `redis` / `mybatis` / `mq` / `security`。
- 禁止写任何业务代码、业务实体、业务表结构、服务专属常量；业务代码放对应 `atifah-services/<service>`。

## 依赖方向

- `common-core` 是基础模块，不依赖其他 common 模块。
- `common-web` / `common-redis` / `common-mybatis` / `common-mq` / `common-security` 只依赖 `common-core`。
- 禁止在 common 模块之间新增横向依赖；确需复用时先抽到 `common-core` 并评估影响。
- 新增第三方依赖先问人；禁止引入第二套 JSON / 日志 / HTTP 客户端。

## 自动装配

- 每个公共能力通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册配置类。
- **不要**在业务服务里加 `@ComponentScan("com.atifah")`；公共能力应依赖 classpath 自动生效。
- 自动配置必须加条件注解（`@ConditionalOnClass` / `@ConditionalOnMissingBean` / `@ConditionalOnWebApplication`），避免污染不需要它的服务。
- `common-web` 只适用于 servlet 栈；`atifah-gateway` 不得依赖它。

## 各模块边界

| 模块 | 职责 | 禁止 |
| --- | --- | --- |
| `common-core` | `Result` / `PageResult` / `ErrorCode` / `BusinessException` / `CommonConstants` | 依赖 Spring Web / MyBatis / Redis |
| `common-web` | 全局异常、TraceId、Sentinel、Actuator | 被网关依赖 |
| `common-redis` | Redis 序列化、`CacheKeys`、Redisson | 业务缓存逻辑 |
| `common-mybatis` | 分页插件、字段填充、`BaseEntity`、Flyway | 具体业务 Mapper / 表结构 |
| `common-mq` | `MqTopics` / `MqConsumerGroups` / `MessagePublisher` | 业务消费者 |
| `common-security` | JWT 签发校验、`PasswordEncoder` | 真实密钥、业务鉴权规则 |

## 兼容性

- `Result` / `PageResult` / `ErrorCode` / `CommonConstants` / `CacheKeys` / `MqTopics` / `MqConsumerGroups` 是全服务契约；修改前先同步 `docs/api-contract.md`、`.claude/rules/*` 与前端 `src/api/types.ts`。
- 新增缓存 Key / Topic 必须先登记再引用；删除或改名视为破坏性变更，需评估所有服务。
- `JWT_SECRET` 只能通过环境变量注入，默认值仅限本地开发。

## 自检

- 改完公共模块后至少执行 `mvn -o -q -DskipTests compile`，并确认受影响的业务服务仍能编译。
- 不通过不得声称完成；禁止删除测试、关校验、加 `@SuppressWarnings` 掩盖问题。
