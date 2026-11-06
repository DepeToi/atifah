# 0002. auth-service 注册、登录与令牌设计

- 状态：已采纳
- 日期：2026-10-19
- 最后更新：2026-10-19（新增 §5.1 并发幂等语义）
- 决策人：项目维护者

> 本文记录决策与理由；令牌与幂等的契约正文以 `docs/contracts/` 为准。

## 背景

`auth-service` 目前只有启动类；网关鉴权默认关闭（`atifah.gateway.auth.enabled=false`）；`common-security` 的 `JwtTokenProvider` 只提供 HS256 access token 签发与解析，不含 `jti` / `type`；`common-redis` 已提供 `RedisTemplate` 与 `CacheKeys`；`common-security` 已提供 BCrypt `PasswordEncoder`。

第一个可交付垂直切片需要跑通「注册 → 登录 → 拿到 token」，同时满足：

- 情绪产品对隐私与安全的要求；
- 一服务一库、网关唯一入口、禁止跨库；
- 单任务 diff ≤400 行，便于 Codex 评审与自测试（规模阈值已由 ADR 0004 调整为 Feature ≤600 行非测试代码）；
- 不阻塞后续 refresh / logout / 网关鉴权。

## 决策

### 1. 切片拆分

| 任务 | 范围 |
| --- | --- |
| **T-001** | `auth-service` 注册 + 登录 + 签发 access/refresh token |
| T-002 | refresh token 轮换 + logout |
| T-003 | 网关鉴权启用 + 精确白名单 + access token 撤销策略 |
| T-004 | 用户资料创建（`user-registered` 事件 / user-service 对接） |

T-001 不修改网关、公共模块、其他服务；只写 `atifah-services/auth-service/**`。

### 2. 账号归属与数据边界

- `auth-service` 拥有 `t_auth_account`：登录标识（手机号）、BCrypt 密码哈希、账号状态、全局 `user_id`。
- 用户昵称、头像、隐私设置归 `user-service`，登录链路**不同步调用** user-service，避免首个切片产生跨服务依赖。
- `user_id` 由 auth-service 在注册时用雪花 ID 生成，作为跨服务全局用户 ID；user-service 后续按该 ID 创建资料。
- 不做跨库 JOIN；`user_id` 是逻辑外键，不建数据库外键。

### 3. 令牌模型

**Access Token（JWT，HS256，15 分钟）**

- 复用现有 `JwtTokenProvider.generateToken(userId, username)`，不修改 `common-security`。
- Claims：`iss`、`sub`（userId）、`username`（暂用 userId 字符串占位）、`iat`、`exp`。
- **不写入手机号、昵称等 PII**；网关从 `sub` / `username` 写 `X-User-Id` / `X-Username`。
- TTL 通过 `atifah.security.jwt.access-token-ttl` 统一为 `15m`（Nacos 共享配置与默认值均已调整）。
- 代价：当前无 `jti`，access token 在 15 分钟内无法立即撤销；立即撤销放到 T-003（网关侧黑名单或升级 `JwtTokenProvider`）。

**Refresh Token（不透明随机串，7 天）**

- 32 字节 `SecureRandom`，Base64 URL 无填充；客户端只拿到原始串。
- 服务端只存 **SHA-256 哈希**：Redis key `CacheKeys.AUTH_REFRESH`（`auth:refresh:<hash>`），value 为 `userId`，TTL 7 天。
- 登录时签发并在 Redis 落库；T-002 实现刷新时轮换（删除旧 hash、签发新 token）。
- 登出（T-002）删除 refresh hash；access token 继续自然过期。
- 不在数据库建 refresh token 表；Redis 丢失时用户重新登录即可。

### 4. 登录防护与错误语义

| 场景 | 返回 | 说明 |
| --- | --- | --- |
| 手机号不存在 / 密码错误 | `40100` + “手机号或密码错误” | 两种情况同一文案，避免用户枚举 |
| 账号禁用（密码正确后） | `40300` + “账号已禁用” | 先验密码，避免无密码探测账号状态 |
| 连续失败 5 次 / 10 分钟 | `42900` + “登录失败次数过多，请稍后再试” | Redis `CacheKeys.AUTH_LOGIN_FAIL`；成功登录清零 |
| 注册手机号已存在 | `40900` + “手机号已注册” | 唯一索引兜底 |
| 参数错误 | `40000` | Jakarta Validation 统一处理 |

- 失败计数使用 `StringRedisTemplate` 的 `INCR` + 首次设置 TTL，避免 JSON 序列化与 `INCR` 不兼容；Redis key 使用手机号的 SHA-256 哈希，避免 PII 进入键空间。
- 当前按手机号计数；存在“恶意锁定他人账号”的风险。IP/设备维度限流放到 T-003/T-004，通过网关转发真实客户端 IP 后实现。
- 日志只记录 userId / 失败类别，不记录手机号、密码、token。

### 5. 幂等规则

- **注册**：必须携带 `Idempotency-Key`；实现使用 Redis `CacheKeys.IDEMPOTENT`（prefix `auth:register`，24h）保存 userId，串行重复请求返回首次结果，手机号唯一索引兜底。格式与并发语义见 `docs/contracts/api-conventions.md` §7。
- **登录 / 刷新 / 登出**：豁免 `Idempotency-Key`；例外清单以契约 §7 为准。

#### 5.1 并发幂等语义（2026-10-19 维护者确认）

- **决策**：并发相同幂等键由 `t_auth_account` 手机号唯一索引兜底，冲突方捕获 `DuplicateKeyException` 后**有界短暂重读**幂等结果；不引入 Redis 原子占位（SETNX）或额外错误码。具体重读次数、间隔、总等待与错误码以 `docs/contracts/api-conventions.md` §7 为准，本 ADR 不重复契约正文。
- **理由**：注册是低频写接口，严格 SETNX 占位的复杂度与事务/Redis 一致性成本高于收益；有界重读覆盖实际竞态窗口，契约措辞明确限定「串行重试」保证。
- **同步范围**：`docs/contracts/api-conventions.md` §7、`docs/contracts/openapi/auth-service.yaml`（register / 409 / IdempotencyKey）、`docs/rules/security.md` SEC-04；实现跟进见 `docs/tasks/T-005-auth-register-idempotency-hardening.md`。
- **版本**：契约语义澄清，请求/响应结构不变；随 `arch-baseline-v0.2` 冻结。

### 6. 网关集成（T-003，不在 T-001）

- 当前白名单 `/api/v1/auth/` 是**前缀匹配**，会把 logout 也放行；T-003 必须改为精确路径：
  - 免鉴权：`/api/v1/auth/register`、`/api/v1/auth/login`、`/api/v1/auth/refresh`
  - 需鉴权：`/api/v1/auth/logout` 及其余业务接口
- 生产环境不应把 `/actuator/` 暴露在公网；T-003 应收敛或迁到管理端口。
- T-003 再决定 access token 撤销方案（网关检查 Redis 黑名单，或升级 `JwtTokenProvider` 增加 `jti`/`type`）。

### 7. 配置

| 配置 | 位置 | 值 |
| --- | --- | --- |
| `atifah.security.jwt.secret` | Nacos `common-application.yaml` / `JWT_SECRET` | 生产必须 256 位以上随机值，auth 与 gateway 必须一致 |
| `atifah.security.jwt.access-token-ttl` | Nacos 共享配置 + `JwtProperties` 默认值 | `15m` |
| `atifah.auth.refresh-token-ttl` | auth-service 本地 `application.yml` | `7d` |
| `atifah.auth.max-login-failures` | auth-service 本地 `application.yml` | `5` |
| `atifah.auth.login-failure-window` | auth-service 本地 `application.yml` | `10m` |

> 修改 `deploy/nacos/config/common-application.yaml` 后必须重新导入 Nacos 并重启/刷新服务，否则仍使用旧值。

### 8. 数据模型

- `t_auth_account`：`id`、`user_id`、`phone`、`password_hash`、`status`、`last_login_at`、审计字段。
- 手机号明文存储 + 唯一索引，接口与日志一律脱敏；登录需要精确匹配，暂不做可搜索加密。
- 逻辑删除后手机号仍被唯一索引占用，T-001 不支持注销后立即重新注册；账号注销/手机号复用需要单独 ADR。
- 刷新令牌不落库；仅在 Redis。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| Session + Cookie | 服务端可即时撤销 | App/管理端多端、网关无状态、跨域成本高 | 否决 |
| 不透明 access token + 每次内省 | 可即时撤销 | 网关每次请求都要访问 auth-service，增加延迟与耦合 | 否决 |
| JWT access token + JWT refresh token | 无状态、统一格式 | 需扩展 `JwtTokenProvider` 支持 `jti`/`type`，改动公共模块 | 暂不采纳；T-003 可升级 |
| JWT access token + 不透明 refresh token（本 ADR） | 不改公共模块；refresh 可撤销、可轮换；实现简单 | access token 15 分钟内不可撤销；Redis 丢失需重新登录 | 采纳 |
| refresh token 存数据库 | 可审计、可管理设备 | 每次刷新多一次 DB 写；首个切片成本高 | 暂不采纳；设备管理时再评估 |
| 手机号字段级加密 | 静态数据更安全 | 精确登录查询需要额外 hash 列/索引，T-001 复杂度上升 | 暂不采纳；合规要求升级时再评估 |
| 短信验证码登录优先 | 无需密码 | 依赖短信供应商，首切片成本高 | 暂不采纳；后续追加 |

## 影响

- 正面：
  - T-001 可独立交付、独立评审，不触碰网关和公共模块。
  - 注册与登录形成闭环，便于端到端联调与测试。
  - refresh token 可轮换、可撤销，且不落明文。
  - 规则与契约明确，Codex 可按 `SEC / API / DB / CODE` 编号逐条检查，并独立执行自测试。
- 负面/代价：
  - access token 在 15 分钟内无法立即撤销（登出后仍可能有效至过期）。
  - 手机号维度失败计数可能被用于恶意锁定。
  - 手机号明文存储；逻辑删除后不能立即复用。
  - 注册只创建认证账号，用户资料需 T-004 补齐。
- 需要同步的文档与代码：
  - `docs/contracts/openapi/auth-service.yaml`
  - `docs/contracts/db/auth-service.sql`
  - `docs/tasks/T-001-auth-login.md`
  - `docs/contracts/api-conventions.md` §7（认证接口幂等例外）
  - `docs/rules/security.md` SEC-04、`docs/review/standards.md` API-04
  - `CacheKeys` 新增 `AUTH_REFRESH` / `AUTH_LOGIN_FAIL`
  - `JwtProperties` 默认 TTL 与 Nacos 共享配置改为 15m
- 回滚：
  - 删除 T-001 新增的 controller/service/mapper/migration，保留契约；
  - 同步回滚 `CacheKeys` 与 TTL 配置；
  - 不影响网关（T-001 不启用鉴权）。

## 关联

- 接口契约：[`../contracts/openapi/auth-service.yaml`](../contracts/openapi/auth-service.yaml)
- 数据库契约：[`../contracts/db/auth-service.sql`](../contracts/db/auth-service.sql)
- 任务包：[`../tasks/T-001-auth-login.md`](../tasks/T-001-auth-login.md)
- 通用规则：[`../contracts/api-conventions.md`](../contracts/api-conventions.md)
- 上级决策：[`0001-three-tool-collaboration.md`](0001-three-tool-collaboration.md)
