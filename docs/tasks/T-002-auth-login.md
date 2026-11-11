---
id: T-002
title: auth 手机号登录
status: done
owner: codebuddy
created: 2026-11-11
updated: 2026-11-11
status_reason: PR #15 已合并，CI 与 Codex 评审通过
contract_baseline: arch-baseline-v0.1
related_adr: [docs/adr/0003-auth-baseline.md]
---

# T-002 auth 手机号登录

## 1. 目标与非目标

- 一句话目标：按契约实现 `POST /api/v1/auth/login`，完成手机号密码登录、失败锁定、Access Token 签发、Refresh Token 落库与 `last_login_at` 更新。
- 业务背景：T-001 注册切片已合并；本任务实现 auth v0.1 的第二个切片，刷新与登出留给 T-003。
- 关联需求 / Issue：`TODO.md` P0-11 后续、T-002。
- 关联 ADR：`docs/adr/0003-auth-baseline.md`（HS256/15m Access Token、不透明 Refresh Token、失败计数 5 次/10m、手机号明文+脱敏）。
- 明确不做：刷新、登出、短信验证码、三方登录、user-service 资料初始化、网关鉴权开启、密码找回。

## 2. 上下文与必读（只列路径）

- 规则：`AGENTS.md`、`docs/rules/security.md`、`docs/rules/code-style.md`、`docs/rules/database.md`、`atifah-services/AGENTS.md`
- 契约：`docs/contracts/openapi/auth-service.yaml`（operationId: `login`）、`docs/contracts/db/auth-service.sql`（表 `t_auth_account` / `t_auth_refresh_token`）、`docs/contracts/api-conventions.md` §4/§6
- 现有实现 / 测试：`com.atifah.auth.controller.AuthController`、`AuthServiceImpl`、`AuthAccountMapper` 及其 XML、`AuthIdempotencyGuard`、`AuthControllerTest`、`AuthServiceImplTest`、`AuthIdempotencyGuardTest`
- 公共能力：`common-security`（`JwtTokenProvider` / `JwtProperties` / `PasswordEncoder`）、`common-redis`（`CacheKeys.AUTH_LOGIN_FAIL`）、`common-web`（`GlobalExceptionHandler`，已支持框架级 400）
- 关联 ADR：`docs/adr/0003-auth-baseline.md`

## 3. 变更范围

### 允许修改

```text
atifah-services/auth-service/**
docs/tasks/T-002-auth-login.md   # 仅 front matter（status / updated / status_reason）
```

> 不需要新增依赖；JWT、Redis、MyBatis、测试依赖均已由父 POM 提供。如发现必须新增，停止实现并标记 `[Arch]`。

### 禁止修改

```text
docs/contracts/**
docs/rules/**
docs/adr/**
根 pom.xml
atifah-common/**
其他服务/**
docs/tasks/ 下其他任务包
deploy/**
.github/**
README.md
```

## 4. 实现要点

- **迁移**：新增 `src/main/resources/db/migration/V2__auth_refresh_token.sql`，逻辑结构必须与 `docs/contracts/db/auth-service.sql` 的 `t_auth_refresh_token` 一致；禁止修改 V1。
- **实体**：`AuthRefreshToken extends BaseEntity`，字段 `userId`、`tokenHash`、`expiresAt`、`revokedAt`；Lombok 只用于样板代码。
- **Mapper**：新增 `AuthRefreshTokenMapper`（不继承 `BaseMapper`），本任务只声明 `insert`；XML 放 `resources/mapper/AuthRefreshTokenMapper.xml`，显式列 `id` / `user_id` / `token_hash` / `expires_at`，时间与 `deleted` 交给数据库默认值。
- **账号 Mapper**：`AuthAccountMapper` 增加 `updateLastLoginAt(@Param("id") Long id, @Param("lastLoginAt") LocalDateTime lastLoginAt)`；XML 更新必须带 `deleted = 0`。
- **DTO / VO**：`LoginRequest`（record + `@Valid`：手机号格式；密码 `@NotBlank` + `@Size(min=8, max=64)`）与 `AuthToken`（record：`userId` 字符串、`accessToken`、`refreshToken`、`tokenType=Bearer`、`expiresIn`、`refreshExpiresIn`）。
- **登录编排** `AuthServiceImpl.login(LoginRequest)`：
  1. `LoginFailureGuard.assertNotLocked(phone)`。
  2. `selectByPhone`；账号不存在时仍执行一次 BCrypt 比对（固定 dummy hash），避免明显的时间差，然后记录失败并返回 `40100`「手机号或密码错误」。
  3. 密码不匹配：记录失败并返回 `40100`；不要暴露账号是否存在。
  4. 密码匹配：先清除失败计数；`status != 0` 返回 `40300`「账号已禁用」。
  5. 调用独立的 `LoginTokenWriter.issue(account)` 完成数据库写入并返回 `AuthToken`。
- **失败锁定** `LoginFailureGuard`（`service` 包）：
  - 使用 `StringRedisTemplate` + `CacheKeys.AUTH_LOGIN_FAIL`，Key 为登录标识的 SHA-256 摘要；哈希逻辑与 `AuthIdempotencyGuard` 保持一致，可抽取局部 `PhoneHasher`，但不得改变注册行为与测试。
  - 读取 `atifah.auth.max-login-failures`（默认 5）与 `atifah.auth.login-failure-window`（默认 10m），禁止硬编码阈值。
  - 前 5 次失败返回 `40100`；计数达到 5 后，窗口内后续请求返回 `42900`「登录失败次数过多，请稍后再试」；成功登录清除计数。
  - Redis 不可用时向上抛出，返回 500，不降级绕过锁定。
- **Token 落库** `LoginTokenWriter`（独立 `@Service`，`@Transactional(rollbackFor = Exception.class)`）：
  - Refresh Token：`SecureRandom` 256 位，Base64URL 无填充；只把 SHA-256 小写十六进制哈希写入 `t_auth_refresh_token`，有效期读取 `atifah.auth.refresh-token-ttl`（默认 7d）；原始令牌只返回客户端，禁止落库/日志。
  - Access Token：`JwtTokenProvider.generateToken(userId, maskPhone(phone))`，`username` claim 用 `138****5678` 形式；`expiresIn` 取 `JwtProperties.accessTokenTtl` 秒数。
  - 同一事务内插入 Refresh Token 并更新 `last_login_at`；**Redis 操作不得放进事务**（DB-04），失败计数清除在调用 writer 之前完成。
- **登录接口**：`AuthController` 增加 `@PostMapping("/login")`，`@Valid @RequestBody LoginRequest`，返回 `Result<AuthToken>`；不要求 `Idempotency-Key`。
- **清理临时兜底**：删除 `AuthExceptionHandler` 及其在 `AuthControllerTest` 中的引用；`common-web` 已统一处理请求体不可读等框架级 400。更新测试，日志断言改为捕获 `GlobalExceptionHandler`，确保日志不含手机号/密码。
- **日志与错误**：只记录 `userId` 与结果状态；错误码取 `ErrorCode`，用 `BusinessException(ErrorCode.X.getCode(), message)`（现有构造器没有 `(ErrorCode, String)`），禁止硬编码数字。
- **规模**：生产语义行目标 ≤400（测试不计入，ADR 0004）；如实现预计超限，先停止并标记 `[Arch]` 回 Claude 拆分。

## 5. 验收标准（可验证）

- [ ] 登录成功：`code=0`，`data` 含字符串 `userId`、JWT `accessToken`、不透明 `refreshToken`、`tokenType=Bearer`、`expiresIn=900`、`refreshExpiresIn=604800`。
- [ ] 数据库中只出现 Refresh Token 的 SHA-256 哈希；无明文令牌；`last_login_at` 已更新。
- [ ] 错误手机号/密码统一返回 `40100`；不泄露账号是否存在；账号不存在路径也执行 dummy BCrypt 比对。
- [ ] 密码正确但账号禁用返回 `40300`；密码错误仍返回 `40100`。
- [ ] 同一手机号 10 分钟内失败 5 次后，后续请求返回 `42900`；成功登录后计数清零。
- [ ] 请求体缺失/非法 JSON 由 `common-web` 返回 `40000`，不再依赖 `AuthExceptionHandler`。
- [ ] 日志、异常 message 不含手机号、密码、Access Token、Refresh Token 明文。
- [ ] 只修改允许路径；未新增依赖；V1 未修改。
- [ ] `mvn -pl atifah-services/auth-service -am test`、`mvn -o -q -DskipTests compile`、`git diff --check` 通过。

## 6. 测试与自检

- 验证级别：A（Feature + 安全相关）。
- 单元测试（Mockito，不依赖 MySQL/Redis/Nacos）：
  - `AuthServiceImplTest`：登录成功、手机号不存在、密码错误、账号禁用、失败计数达到阈值返回 429、成功清除计数、Redis 异常向上抛出。
  - `LoginFailureGuardTest`：阈值判断、INCR + TTL、成功清除、SHA-256 Key 构造。
  - `LoginTokenWriterTest`：Refresh Token 只存哈希、有效期 7d、`last_login_at` 更新、Access Token username 为脱敏手机号；事务边界由代码审查确认。
  - `AuthControllerTest`：登录成功、参数错误 400、凭据错误 401、禁用 403、锁定 429；删除 `AuthExceptionHandler` 相关断言，改为 `GlobalExceptionHandler`。
- 测试代码不计入 SCOPE-02，但需在 PR 描述中说明测试规模。
- 自检命令：

```bash
mvn -pl atifah-services/auth-service -am test
mvn -o -q -DskipTests compile
git diff --check
```

## 7. 风险与回滚

- 风险：
  - 登录依赖 Redis 失败计数；Redis 不可用时返回 500，不绕过锁定。
  - Refresh Token 原始值只在响应中出现；如日志误打会泄露，需测试与代码审查双重确认。
  - 本任务不实现刷新/登出；契约中的 `/refresh`、`/logout` 暂不可用。
  - `AuthExceptionHandler` 删除后依赖 `common-web` 的框架级 400 映射（PR #11 已合并）。
- 回滚方式：revert 本 PR；V2 迁移为新增表，可回滚删除；无存量生产数据。
- 数据迁移影响：新增 `t_auth_refresh_token`，不改 `t_auth_account`。
