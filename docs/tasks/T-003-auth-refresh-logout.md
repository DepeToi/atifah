---
id: T-003
title: auth 刷新与登出
status: ready
owner: codebuddy
created: 2026-11-11
updated: 2026-11-11
status_reason: 维护者指示开启 T-003；契约已冻结（arch-baseline-v0.1）
contract_baseline: arch-baseline-v0.1
related_adr: [docs/adr/0003-auth-baseline.md]
---

# T-003 auth 刷新与登出

## 1. 目标与非目标

- 一句话目标：按契约实现 `POST /api/v1/auth/refresh` 与 `POST /api/v1/auth/logout`，完成 Refresh Token 轮换、30 秒宽限、重用检测与幂等登出。
- 业务背景：T-002 登录已合并；本任务补齐 auth v0.1 的最后两个接口。
- 关联需求 / Issue：`TODO.md` P0-11 后续、T-003。
- 关联 ADR：`docs/adr/0003-auth-baseline.md`（不透明 Refresh Token、7d、30s 宽限、重用检测撤销全部有效令牌、登出不改 Access Token TTL）。
- 明确不做：注册、登录、短信验证码、三方登录、user-service 资料初始化、网关鉴权开启、密码找回、设备管理。

## 2. 上下文与必读（只列路径）

- 规则：`AGENTS.md`、`docs/rules/security.md`、`docs/rules/code-style.md`、`docs/rules/database.md`、`atifah-services/AGENTS.md`
- 契约：`docs/contracts/openapi/auth-service.yaml`（operationId: `refresh` / `logout`）、`docs/contracts/db/auth-service.sql`（表 `t_auth_refresh_token`）、`docs/contracts/api-conventions.md` §4/§6
- 现有实现 / 测试：`AuthRefreshTokenMapper` 及其 XML、`AuthRefreshToken`、`LoginTokenWriter`、`AuthProperties`、`AuthServiceImpl`、`AuthController`、`AuthToken`、`V2__auth_refresh_token.sql`、`AuthControllerTest`、`AuthServiceImplTest`、`LoginTokenWriterTest`
- 公共能力：`common-security`（`JwtTokenProvider` / `JwtProperties`）、`common-core`（`ErrorCode` / `BusinessException` / `Result`）
- 关联 ADR：`docs/adr/0003-auth-baseline.md`

## 3. 变更范围

### 允许修改

```text
atifah-services/auth-service/**
docs/tasks/T-003-auth-refresh-logout.md   # 仅 front matter（status / updated / status_reason）
```

> 不需要新增依赖；本任务不引入 Redis 调用。如发现必须新增，停止实现并标记 `[Arch]`。

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

- **配置**：`AuthProperties` 增加 `Duration refreshReuseGrace = Duration.ofSeconds(30)`（可绑定 `atifah.auth.refresh-reuse-grace`）；不要求改 `application.yml`，默认值生效。
- **Mapper**：`AuthRefreshTokenMapper` 增加：
  - `AuthRefreshToken selectByTokenHash(@Param("tokenHash") String tokenHash)`
  - `int revoke(@Param("id") Long id, @Param("revokedAt") LocalDateTime revokedAt)`：`WHERE id = ? AND revoked_at IS NULL AND deleted = 0`
  - `int revokeAllActiveByUserId(@Param("userId") Long userId, @Param("revokedAt") LocalDateTime revokedAt)`：`WHERE user_id = ? AND revoked_at IS NULL AND deleted = 0`
  - XML 显式列名、`#{}` 绑定；查询与更新都带 `deleted = 0`；更新只写 `revoked_at`，`updated_at` 交给数据库。
- **账号 Mapper**：`AuthAccountMapper` 增加 `selectByUserId(@Param("userId") Long userId)`，查询带 `deleted = 0`。
- **令牌工具**：从 `LoginTokenWriter` 抽取 `TokenFactory`（`service` 包）：
  - `String newRefreshToken()`：`SecureRandom` 256 位，Base64URL 无填充。
  - `String hash(String token)`：SHA-256 小写十六进制。
  - `AuthToken buildAuthToken(AuthAccount account, String rawRefreshToken)`：生成 Access Token，`username` 用脱敏手机号，TTL 取 `JwtProperties` / `AuthProperties`。
  - `LoginTokenWriter` 改为使用 `TokenFactory`，行为与现有测试保持一致；删除重复的随机数/哈希/脱敏代码。
- **刷新服务**：新增 `RefreshTokenService`（`service` 包），`refresh(RefreshTokenRequest)`：
  1. 计算哈希并 `selectByTokenHash`；不存在或已过期 → `40100`「刷新令牌无效或已过期」。
  2. 已撤销：`revokedAt >= now - reuseGrace` → `40100`；超过宽限 → 调用 `RefreshTokenRevoker.revokeAllActive(userId)` 后 `40100`。
  3. 按 `userId` 查账号；不存在或 `status != 0` → `40100`（refresh 契约未声明 40300）。
  4. 条件撤销旧令牌：`revoke(oldId, now)`；返回 0 表示被并发刷新抢先，直接 `40100`。
  5. 生成新 Refresh Token / 哈希 / `expiresAt = now + refreshTokenTtl`，插入新会话，构造并返回 `AuthToken`。
  - 方法加 `@Transactional(rollbackFor = Exception.class)`；该流程没有 Redis 调用。
- **重用检测事务**：新增 `RefreshTokenRevoker`，`revokeAllActive(userId)` 使用 `@Transactional(propagation = Propagation.REQUIRES_NEW)`，确保“撤销全部有效令牌”在抛出 `40100` 前独立提交，不被外层回滚。
- **登出服务**：`RefreshTokenService.logout(RefreshTokenRequest)`：哈希 → 查询；不存在/过期/已撤销 → 直接成功；活动令牌 → `revoke(id, now)`，返回 0 也视为成功。不校验 Access Token。
- **DTO / 接口**：新增 `RefreshTokenRequest`（record，`@NotBlank` + `@Size(min=43, max=128)`），refresh 与 logout 共用；`AuthService` 增加 `refresh` / `logout`，`AuthServiceImpl` 只做委托。
- **Controller**：新增 `@PostMapping("/refresh")` 与 `@PostMapping("/logout")`，`@Valid @RequestBody`；分别返回 `Result<AuthToken>` 与 `Result<Void>`；不要求 `Idempotency-Key`。
- **日志与错误**：只记录 `userId` / 结果状态；禁止打印原始令牌、令牌哈希、手机号；错误码取 `ErrorCode`，禁止硬编码。
- **规模**：生产语义行目标 ≤400（测试不计入，ADR 0004）；如预计超限，先停止并标记 `[Arch]`。

## 5. 验收标准（可验证）

- [ ] 刷新成功：旧 Refresh Token 被撤销，新会话只存 SHA-256 哈希；返回新的 Access/Refresh Token，`expiresIn=900`、`refreshExpiresIn=604800`。
- [ ] 不存在/过期/30 秒宽限内已撤销的 Refresh Token → `40100`，不触发全量撤销。
- [ ] 超过 30 秒再次使用已撤销令牌 → 撤销该用户全部有效刷新令牌，返回 `40100`。
- [ ] 并发刷新同一令牌：只有一个请求成功，其他返回 `40100`。
- [ ] 账号不存在或禁用时刷新 → `40100`，不签发新令牌。
- [ ] 登出活动令牌 → 撤销；重复登出、未知/过期/已撤销令牌 → 成功（幂等）。
- [ ] 登出后 Access Token 在剩余 TTL 内仍有效（不在本任务引入 `jti`）。
- [ ] 日志、异常 message、数据库中无原始令牌明文；V2 迁移未修改。
- [ ] 只修改允许路径；未新增依赖。
- [ ] `mvn -pl atifah-services/auth-service -am test`、`mvn -o -q -DskipTests compile`、`git diff --check` 通过。

## 6. 测试与自检

- 验证级别：A（Feature + 安全相关）。
- 单元测试（Mockito，不依赖 MySQL/Redis/Nacos）：
  - `RefreshTokenServiceTest`：刷新成功轮换、过期、宽限内撤销、超过宽限触发全量撤销、并发撤销返回 0、账号不存在/禁用、登出活动/幂等。
  - `TokenFactoryTest`：随机令牌长度、SHA-256 哈希、Access Token 的脱敏 username。
  - `AuthControllerTest`：refresh 成功/400/401，logout 成功/400；确认不要求 `Idempotency-Key`。
  - 现有登录/注册测试在 `TokenFactory` 抽取后保持通过。
- 测试代码不计入 SCOPE-02，但需在 PR 描述中说明测试规模。
- 自检命令：

```bash
mvn -pl atifah-services/auth-service -am test
mvn -o -q -DskipTests compile
git diff --check
```

## 7. 风险与回滚

- 风险：
  - 轮换并发由条件撤销兜底；失败方返回 `40100`，不触发全量撤销。
  - 重用检测会撤销该用户全部有效令牌，属于预期的安全行为。
  - 登出不立即失效 Access Token，最长剩余 TTL（≤15 分钟）；`jti` 黑名单不在本任务。
  - `RefreshTokenRevoker` 使用 `REQUIRES_NEW`，需代码审查确认全量撤销在 401 前提交。
- 回滚方式：revert 本 PR；本任务不新增表、不修改 V2，无数据迁移。
- 数据迁移影响：无（V2 由 T-002 创建）。
