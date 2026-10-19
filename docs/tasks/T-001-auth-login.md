---
id: T-001
title: auth-service 注册与登录（签发 access / refresh token）
status: in-review
owner: codebuddy
created: 2026-10-19
contract_baseline: arch-baseline-v0.1（待人工确认后打标签）
related_adr:
  - docs/adr/0002-auth-login-token.md
---

# T-001 auth-service 注册与登录

## 1. 目标

在 `atifah-services/auth-service` 实现：

1. `POST /api/v1/auth/register`：创建认证账号，返回全局 `userId`。
2. `POST /api/v1/auth/login`：手机号 + 密码登录，返回 access token 与 refresh token。
3. refresh token 以 SHA-256 哈希形式存入 Redis，TTL 7 天。
4. 登录失败计数与基础防刷（5 次 / 10 分钟）。

完成后，用户可以通过真实 HTTP 接口完成「注册 → 登录 → 拿到可被网关校验的 JWT」。

## 2. 非目标（本任务明确不做）

- `POST /api/v1/auth/refresh`（T-002）
- `POST /api/v1/auth/logout`（T-002）
- 网关鉴权开启、白名单调整（T-003）
- user-service 用户资料创建 / `user-registered` 事件（T-004）
- 短信验证码、三方登录、MFA、密码找回、手机号换绑
- access token 黑名单 / `jti` 改造（T-003 再评估）
- 手机号字段级加密、账号注销后的手机号复用
- 管理后台登录

## 3. 必读文件（按顺序）

- [ ] `AGENTS.md`
- [ ] `docs/rules/security.md`
- [ ] `docs/rules/code-style.md`
- [ ] `docs/rules/database.md`
- [ ] `docs/rules/git-workflow.md`
- [ ] `atifah-services/AGENTS.md`
- [ ] `docs/adr/0002-auth-login-token.md`
- [ ] `docs/contracts/api-conventions.md`
- [ ] `docs/contracts/openapi/auth-service.yaml`（`/register`、`/login`）
- [ ] `docs/contracts/db/auth-service.sql`
- [ ] 现有公共类：`JwtTokenProvider`、`JwtProperties`、`PasswordEncoder`、`CacheKeys`、`Result`、`ErrorCode`、`BusinessException`、`BaseEntity`、`GlobalExceptionHandler`

## 4. 变更范围

### 允许修改

```text
atifah-services/auth-service/**
docs/tasks/T-001-auth-login.md   # 仅允许更新 front matter 的 status 字段
```

### 禁止修改

```text
docs/contracts/**
docs/rules/**
docs/adr/**
根 pom.xml
atifah-common/**
atifah-gateway/**
其他 atifah-services/* 服务
deploy/**
.github/**
```

> `CacheKeys.AUTH_REFRESH` / `CacheKeys.AUTH_LOGIN_FAIL` 已由架构侧预先登记，直接引用，不要修改公共模块。

## 5. 接口与数据契约

- 注册：`POST /api/v1/auth/register`
  - Header：`Idempotency-Key`（必填，UUID）
  - 请求：`{ "phone": "13800138000", "password": "Passw0rd!" }`
  - 成功：`Result<{ userId: string }>`
  - 错误：`40000` 参数/缺少幂等键；`40900` 手机号已注册；`42900` 限流
- 登录：`POST /api/v1/auth/login`
  - 请求：`{ "phone": "13800138000", "password": "Passw0rd!" }`
  - 成功：`Result<{ accessToken, refreshToken, tokenType: "Bearer", expiresIn: 900, userId }>`
  - 错误：`40100` 手机号或密码错误；`40300` 账号禁用；`42900` 失败次数过多
- 数据表：`t_auth_account`，字段与索引严格按 `docs/contracts/db/auth-service.sql`。
- Flyway：`src/main/resources/db/migration/V1__init.sql`，内容与契约一致。

## 6. 实现要点

### 6.1 包结构

```text
com.atifah.auth
├── config/AuthProperties.java        # @ConfigurationProperties(prefix = "atifah.auth")
├── config/AuthConfig.java            # @Configuration + @EnableConfigurationProperties(AuthProperties.class)
├── controller/AuthController.java
├── dto/RegisterRequest.java
├── dto/LoginRequest.java
├── entity/AuthAccount.java           # extends BaseEntity, @TableName("t_auth_account")
├── enums/AuthAccountStatus.java      # NORMAL(0), DISABLED(1)
├── mapper/AuthAccountMapper.java     # extends BaseMapper<AuthAccount>
├── service/AuthService.java
├── service/RefreshTokenService.java
├── service/LoginAttemptService.java
├── service/impl/AuthServiceImpl.java
├── vo/AuthTokenVO.java
└── vo/RegisterResponse.java
```

- `AuthController` 使用 `@RestController` + `@RequestMapping("/api/v1/auth")`。
- Service 用构造注入（`@RequiredArgsConstructor`），禁止字段 `@Autowired`。
- `AuthAccount` 继承 `BaseEntity`；`status` 用 `Integer` 存储，引用 `AuthAccountStatus` 枚举，禁止魔法值。
- Mapper 放在 `com.atifah.auth.mapper`，与启动类 `@MapperScan` 一致。

### 6.2 注册流程

1. `Idempotency-Key` 使用 `required = false` 接收，手动校验非空；为空抛 `BusinessException(40000, "缺少 Idempotency-Key")`。
2. 查询幂等键 `CacheKeys.IDEMPOTENT.formatted("auth:register", key)`；已存在则解析 userId 直接返回。
3. 按 `phone` 查询账号；已存在抛 `BusinessException(40900, "手机号已注册")`。
4. `user_id` 用 `IdWorker.getId()` 生成；`id` 由 MyBatis-Plus 雪花策略生成。
5. `passwordEncoder.encode(password)` 后落库；`status = 0`。
6. 把 userId 写入幂等键，TTL 24h。
7. 方法加 `@Transactional`；捕获 `DuplicateKeyException`，若幂等键已有值返回首次结果，否则抛 `40900`。
8. 日志只记录 `userId`，不得记录手机号或密码。

### 6.3 登录流程

1. 先检查失败计数：`LoginAttemptService.assertAllowed(phone)`；达到阈值抛 `BusinessException(42900, "登录失败次数过多，请稍后再试")`。
2. 按 phone 查询账号：
   - 不存在 → 记录失败 → 抛 `40100` + “手机号或密码错误”。
   - 密码不匹配 → 记录失败 → 抛 `40100` + 同一文案。
3. 密码正确但 `status == DISABLED` → 抛 `40300` + “账号已禁用”（不记录失败）。
4. 登录成功：清除失败计数；更新 `last_login_at`（失败只记 warn，不影响登录）。
5. 签发 access token：
   ```java
   String accessToken = jwtTokenProvider.generateToken(
           account.getUserId(), String.valueOf(account.getUserId()));
   ```
   `username` 暂用 userId 占位，禁止写入手机号。
6. 签发 refresh token：`RefreshTokenService.issue(account.getUserId())`。
7. `expiresIn = jwtProperties.getAccessTokenTtl().toSeconds()`（当前 15m → 900）。
8. 记录 `log.info("auth login succeeded userId={}", userId)`。

### 6.4 Refresh Token

- 生成 32 字节 `SecureRandom`，Base64 URL 编码（无填充）。
- 计算 SHA-256 十六进制哈希。
- Redis key：`CacheKeys.AUTH_REFRESH.formatted(hash)`；value：userId 字符串；TTL：`AuthProperties.refreshTokenTtl`（默认 7d）。
- 使用 `StringRedisTemplate`（纯字符串，避免 JSON 序列化问题）。
- 提供 `issue(Long userId)`、`revoke(String rawToken)`、`hash(String rawToken)`。
- **禁止**把原始 refresh token 写入 Redis、日志或数据库。
- T-002 会在此基础实现轮换与登出；本任务只需 `issue` 与 `revoke` 可用。

### 6.5 登录失败计数

- Redis key：`CacheKeys.AUTH_LOGIN_FAIL.formatted(sha256(phone))`，禁止把手机号明文放进 Redis key。
- 每次失败 `INCR`；首次设置 TTL = `AuthProperties.loginFailureWindow`（默认 10m）。
- 达到 `AuthProperties.maxLoginFailures`（默认 5）后，后续登录直接返回 `42900`。
- 登录成功后 `DELETE` 该 key。
- 使用 `StringRedisTemplate`，不要用 JSON 序列化的 `RedisTemplate` 做 `INCR`。

### 6.6 配置

在 `atifah-services/auth-service/src/main/resources/application.yml` 增加：

```yaml
atifah:
  auth:
    refresh-token-ttl: 7d
    max-login-failures: 5
    login-failure-window: 10m
```

- 不要在本文件重复配置 `atifah.security.jwt.secret` / `access-token-ttl`；它们由 Nacos `common-application.yaml` 提供（当前 15m）。
- 生产环境 `JWT_SECRET` 必须由环境变量注入，且 auth-service 与 gateway 一致。
- 修改 `deploy/nacos/config/common-application.yaml` 后需重新导入 Nacos；本任务不修改该文件。

### 6.7 错误与日志

- 业务失败统一抛 `BusinessException`，不要 Controller try-catch。
- 禁止记录手机号、密码、access/refresh token、密码哈希。
- 失败日志只记录类别，例如 `log.warn("auth login failed reason=bad-credentials")`。
- 不引入新依赖；不修改公共模块。

## 7. 测试要求

至少覆盖：

- [ ] 注册成功：落库、返回 userId、密码为 BCrypt 哈希。
- [ ] 注册重复手机号：`40900`。
- [ ] 注册相同 Idempotency-Key 重试：返回首次 userId，不新增记录。
- [ ] 缺少 Idempotency-Key：`40000`。
- [ ] 登录成功：返回 access/refresh token，`expiresIn=900`，userId 正确。
- [ ] 手机号不存在 / 密码错误：均返回 `40100`，文案相同。
- [ ] 禁用账号且密码正确：`40300`。
- [ ] 连续失败 5 次后：后续登录返回 `42900`；成功登录后计数清零。
- [ ] refresh token：Redis key 为 SHA-256 哈希，value 为 userId，TTL 为 7 天；原始 token 不出现在 key 中。
- [ ] access token 可由 `JwtTokenProvider.parseToken` 解析，`sub` 为 userId。

测试建议：

- `AuthServiceImplTest`：Mockito mock `AuthAccountMapper`、`StringRedisTemplate`、`PasswordEncoder`、`JwtTokenProvider`。
- `RefreshTokenServiceTest`：mock `StringRedisTemplate`，验证哈希与 TTL。
- `LoginAttemptServiceTest`：验证 INCR/TTL/清零。
- 至少一个 `@WebMvcTest` 或 `MockMvc` 测试验证参数校验与缺少幂等键的响应。
- 单元测试不依赖真实 MySQL/Redis；Flyway 脚本由构建与本地中间件验证。
- Codex 会在独立工作区复跑 `mvn -pl atifah-services/auth-service -am verify`；测试必须可重复，不得依赖本地临时状态。

## 8. 验收标准

- [ ] 接口行为与 `docs/contracts/openapi/auth-service.yaml` 的 `/register`、`/login` 一致。
- [ ] DDL 与 `docs/contracts/db/auth-service.sql` 一致，Flyway `V1__init.sql` 可执行。
- [ ] access token 为 JWT，`sub` = userId，`exp` = 签发后 15 分钟，不含手机号。
- [ ] refresh token 只以 SHA-256 哈希存 Redis，TTL 7 天。
- [ ] 登录失败计数与 429 行为符合 ADR-0002。
- [ ] 未知手机号与错误密码返回同一 `40100` 文案。
- [ ] 无跨服务调用、无跨库访问、无新增依赖、无公共模块改动。
- [ ] 日志不含手机号、密码、token、密码哈希。
- [ ] `mvn -pl atifah-services/auth-service -am verify` 通过。
- [ ] 只修改“允许修改”路径。

## 9. 自检命令

```bash
cd D:/Project/atifah
mvn -pl atifah-services/auth-service -am verify
git diff --check
git status
```

## 10. 风险与回滚

- **风险**：access token 15 分钟内不可撤销；手机号维度失败计数可能被用于恶意锁定；Redis 丢失导致 refresh token 失效（用户重新登录）；手机号明文存储。
- **回滚**：删除 auth-service 新增代码与 `V1__init.sql`，保留契约；不涉及网关和公共模块，回滚成本低。
- **数据迁移**：首次上线只新增 `t_auth_account`，无历史数据迁移；回滚时删除表即可（需确认无生产数据）。

## 11. 评审记录

| 轮次 | 评审人 | 自测试命令/结果 | 结论 | Blocker/Major | 处理 |
| --- | --- | --- | --- | --- | --- |
| 1 | Codex |  |  |  |  |
