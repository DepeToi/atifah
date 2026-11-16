---
id: T-004
title: auth Redis 调用收敛到 RedisUtil
status: done
owner: codebuddy
created: 2026-11-16
updated: 2026-11-16
status_reason:  PR #21 已合并，CI 与 Codex 评审通过
contract_baseline: arch-baseline-v0.1
related_adr: [docs/adr/0006-common-redis-util.md, docs/adr/0003-auth-baseline.md]
---

# T-004 auth Redis 调用收敛到 RedisUtil

## 1. 目标与非目标

- 一句话目标：将 `AuthIdempotencyGuard` 与 `LoginFailureGuard` 直接注入的 `StringRedisTemplate` 替换为 `RedisUtil`，删除 auth-service 内重复的 Redis 访问与 Lua 脚本，行为保持不变。
- 业务背景：PR #19 已将 `RedisUtil` 合入 `common-redis`（ADR 0006）；auth-service 是当前唯一直接操作 Redis 的业务服务，先收敛这两个组件，验证公共能力可用。
- 关联需求 / Issue：PR #19、ADR 0006。
- 关联 ADR：`docs/adr/0006-common-redis-util.md`（RedisUtil 边界与 TTL 语义）、`docs/adr/0003-auth-baseline.md`（登录失败锁定、注册幂等）。
- 明确不做：
  - 不改注册 / 登录 / 刷新 / 登出接口、错误码、TTL、阈值或 Redis Key 格式；
  - 不改 `common-redis`、`CacheKeys` 或其他服务；
  - 不新增依赖；
  - 不把 `save` 改成 `setIfAbsent`，不改变并发语义；
  - 不引入真实 Redis 集成测试。

## 2. 上下文与必读（只列路径）

- 规则：`AGENTS.md`、`docs/rules/security.md`（SEC-01 / SEC-04）、`docs/rules/code-style.md`（CODE-04 / CODE-09）、`docs/rules/database.md`（DB-05）、`atifah-services/AGENTS.md`、`atifah-common/AGENTS.md`
- 契约：`docs/contracts/openapi/auth-service.yaml`（register / login 的 40000 / 40900 / 42900 错误码）、`docs/contracts/api-conventions.md` §7（幂等）；本任务不改表
- 现有实现 / 测试：
  - `atifah-services/auth-service/src/main/java/com/atifah/auth/service/AuthIdempotencyGuard.java`
  - `atifah-services/auth-service/src/main/java/com/atifah/auth/service/LoginFailureGuard.java`
  - `atifah-services/auth-service/src/test/java/com/atifah/auth/service/AuthIdempotencyGuardTest.java`
  - `atifah-services/auth-service/src/test/java/com/atifah/auth/service/LoginFailureGuardTest.java`
- 公共能力：`com.atifah.common.redis.util.RedisUtil`（`getString` / `setString` / `delete` / `increment(key, delta, ttl)`）、`CacheKeys`、`ErrorCode`、`BusinessException`
- 关联 ADR：ADR 0006、ADR 0003

## 3. 变更范围

### 允许修改

```text
atifah-services/auth-service/src/main/java/com/atifah/auth/service/AuthIdempotencyGuard.java
atifah-services/auth-service/src/main/java/com/atifah/auth/service/LoginFailureGuard.java
atifah-services/auth-service/src/test/java/com/atifah/auth/service/AuthIdempotencyGuardTest.java
atifah-services/auth-service/src/test/java/com/atifah/auth/service/LoginFailureGuardTest.java
docs/tasks/T-004-auth-redis-util-migration.md   # 仅 front matter（status / updated / status_reason）
```

> `RedisUtil` 由 `common-redis` 自动装配；auth-service 已通过父 POM 间接依赖，不需要改 pom。只允许改上述路径，其他一律禁止。

### 禁止修改

```text
docs/contracts/**
docs/rules/**
docs/adr/**
根 pom.xml
atifah-common/**
atifah-services/pom.xml
其他服务/**
docs/tasks/ 下其他任务包
deploy/**
.github/**
README.md
```

## 4. 实现要点

- **AuthIdempotencyGuard**：
  - 字段改为 `private final RedisUtil redisUtil;`，保持 `@Component` + `@RequiredArgsConstructor` 构造注入。
  - `findUserId` 用 `redisUtil.getString(buildKey(idempotencyKey))` 读取。
  - `save` 用 `redisUtil.setString(buildKey(idempotencyKey), value, IDEMPOTENCY_TTL)` 写入。
  - 保持 UUID 校验、`CacheKeys.IDEMPOTENT` Key 格式、SHA-256 指纹、`sha256(phone):userId` 值格式、24h TTL、40000 / 40900 错误码与 message 不变。
  - 不要把 `save` 改成 `setStringIfAbsent`；并发语义保持现状。
- **LoginFailureGuard**：
  - 字段改为 `private final RedisUtil redisUtil;`，删除 `StringRedisTemplate`、`DefaultRedisScript`、`RedisScript`、`List` 导入与私有 `ACQUIRE_ATTEMPT_SCRIPT`。
  - `acquireAttempt` 改为 `long attempts = redisUtil.increment(buildKey(phone), 1L, authProperties.getLoginFailureWindow());`，随后保持 `attempts > maxLoginFailures` 抛 42900 的逻辑与提示语不变。
  - `clear` 改为 `redisUtil.delete(buildKey(phone))`，忽略返回值。
  - 保持 `CacheKeys.AUTH_LOGIN_FAIL` Key 格式、手机号 SHA-256 指纹、窗口与阈值配置读取方式不变。
  - 不把 RedisUtil 的 Lua 脚本复制回业务代码；脚本语义由 `RedisUtilTest.incrementWithTtlExecutesSingleAtomicScript` 覆盖。
  - TTL 语义说明：RedisUtil 在 Key 没有 TTL 时设置过期；旧脚本仅在首次 `INCR` 时设置。对本组件正常创建的 Key 两者一致；若存在历史遗留的无 TTL Key，RedisUtil 会补设 TTL，属于可接受的加固。如评审要求完全保持旧脚本语义，停止实现并标记 `[Arch]`。
  - Redis 不可用或脚本返回空时，RedisUtil 会直接抛异常；不得 try-catch 吞掉，保持 ADR 0003 的失败关闭策略。
- **通用约束**：
  - 日志不得打印手机号、幂等键、指纹或 Redis 值。
  - 不新增缓存 Key、错误码、配置项或依赖。
  - 生产语义行目标 ≤400（测试不计入，ADR 0004）；本任务预计远低于上限。

## 5. 验收标准（可验证）

- [ ] auth-service 两个生产类不再导入或注入 `StringRedisTemplate`、`ValueOperations`、`RedisScript`；Redis 操作全部经 `RedisUtil`。
- [ ] 注册幂等行为不变：相同幂等键 + 相同手机号返回首次 userId；不同手机号返回 40900；无记录返回空；`save` 仍写 24h TTL 且值为 `sha256(phone):userId`。
- [ ] 登录失败锁定行为不变：窗口内前 5 次允许；第 6 次返回 42900「登录失败次数过多，请稍后再试」；Redis 异常向上抛出。
- [ ] `clear` 删除登录失败计数 Key；Key 不含手机号明文。
- [ ] 未修改 `CacheKeys`、错误码、TTL、阈值、配置与契约；未新增依赖。
- [ ] 只修改允许路径。
- [ ] `mvn -pl atifah-services/auth-service -am test`、`mvn -o -q -DskipTests compile`、`git diff --check` 通过。

## 6. 测试与自检

- 验证级别：A（Feature + 安全相关）。
- `AuthIdempotencyGuardTest`（Mockito，不依赖 Redis）：
  - 改为 mock `RedisUtil`，验证 `getString` / `setString` 的 Key、值和 24h TTL；
  - 保留 UUID 校验、Key 模板、同手机号重放、不同手机号 40900、无记录返回空。
- `LoginFailureGuardTest`（Mockito，不依赖 Redis）：
  - 改为 mock `RedisUtil`，验证 `increment(buildKey(phone), 1L, Duration.ofMinutes(10))` 的调用；
  - 保留前 5 次允许、第 6 次 42900、Redis 异常传播、`clear` 删除 Key、Key 不含手机号明文；
  - 删除对 `RedisScript` 正文的断言，不要复制 `RedisUtilTest` 的脚本测试。
- 测试代码不计入 SCOPE-02，但需在 PR 描述中说明测试规模。
- 自检命令：

```bash
mvn -pl atifah-services/auth-service -am test
mvn -o -q -DskipTests compile
git diff --check
```

## 7. 风险与回滚

- 风险：
  - `LoginFailureGuard` 的 TTL 设置条件由「首次 INCR」变为「Key 无 TTL」，对本组件正常创建的 Key 行为一致；历史遗留无 TTL Key 会被补设过期。
  - 脚本测试从 auth-service 移到 `common-redis`，需确认 `RedisUtilTest` 通过；不得在 auth-service 重复断言脚本正文。
  - RedisUtil 的 `increment` 返回原始类型并拒绝空结果，Redis 异常时失败关闭，符合 ADR 0003，但与旧代码“空结果放行”的极端分支不同。
- 回滚方式：revert 本 PR；Key 格式、TTL 与错误码未变，无需数据迁移或清理。
- 数据迁移影响：无。
