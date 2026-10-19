---
id: T-005
title: auth-service 注册幂等加固（UUID 校验 + 并发有界重读）
status: draft
owner: codebuddy
created: 2026-10-19
updated: 2026-10-19
status_reason: 等待契约语义随 arch-baseline-v0.2 冻结并由维护者确认后转 ready
contract_baseline: arch-baseline-v0.2（待契约 PR 合并后人工确认并打标签）
related_adr:
  - docs/adr/0002-auth-login-token.md
---

# T-005 auth-service 注册幂等加固

## 1. 目标与背景

修复 T-001 评审报告 `docs/review/reports/T-001-7ae8964.md` 遗留的 1 个 `Minor` 与 1 个 `Question`：

1. **Minor（API-04）**：`Idempotency-Key` 只校验非空，未校验契约要求的 UUID 格式。
2. **Question（API-04 / SEC-04）**：相同幂等键并发注册同手机号时，第二个请求可能返回 `40900` 而非首次 userId。

维护者已确认采用「有界短重读」语义：不引入 Redis 原子占位或分布式锁，冲突后短暂重读幂等结果，取不到再返回 `40900`。决策记录见 `docs/adr/0002-auth-login-token.md` §5.1。

> 编号说明：ADR-0002 已预留 T-002（refresh/logout）、T-003（网关鉴权）、T-004（用户资料）；本任务为 T-001 评审遗留项，编号使用 T-005。

## 2. 非目标（本任务明确不做）

- `POST /api/v1/auth/refresh`、`POST /api/v1/auth/logout`（T-002）
- 网关鉴权启用与白名单调整（T-003）
- user-service 用户资料创建 / `user-registered` 事件（T-004）
- Redis SETNX / Redisson 分布式锁原子占位、新增错误码
- 修改契约、规则、ADR、公共模块、DDL / Flyway

## 3. 必读文件（按顺序）

- [ ] `AGENTS.md`
- [ ] `docs/rules/security.md`（SEC-04）
- [ ] `docs/rules/code-style.md`
- [ ] `docs/rules/database.md`
- [ ] `docs/rules/git-workflow.md`
- [ ] `atifah-services/AGENTS.md`
- [ ] `docs/adr/0002-auth-login-token.md` §5、§5.1
- [ ] `docs/contracts/api-conventions.md` §7
- [ ] `docs/contracts/openapi/auth-service.yaml`（`/api/v1/auth/register`、`IdempotencyKey`）
- [ ] 现有实现：`atifah-services/auth-service/src/main/java/com/atifah/auth/service/impl/AuthServiceImpl.java`
- [ ] 现有测试：`atifah-services/auth-service/src/test/java/com/atifah/auth/service/impl/AuthServiceImplTest.java`

## 4. 变更范围

### 允许修改

```text
atifah-services/auth-service/src/main/java/com/atifah/auth/**
atifah-services/auth-service/src/test/**
docs/tasks/T-005-auth-register-idempotency-hardening.md   # 仅 front matter status / §11 / §12
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

## 5. 实现要点

### 5.1 Idempotency-Key 格式校验

在 `AuthServiceImpl.register` 现有校验位置实现：

- `null` / 空白 → 保持现状：`BusinessException(40000, "缺少 Idempotency-Key")`。
- 非规范 UUID → `BusinessException(40000, "Idempotency-Key 必须为 UUID")`。
- 规范 UUID 定义：`8-4-4-4-12`，十六进制大小写均可：

```java
private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
```

- 不要使用 `UUID.fromString` 单独判断（会放行 `1-1-1-1-1` 等非规范写法）。
- 校验必须在写 Redis / 查库之前完成。
- 不得把原始 `Idempotency-Key` 写入日志。

### 5.2 并发冲突后的有界重读

保持现有流程不变（先查幂等键 → 查重 → `INSERT` → 写幂等键），仅调整 `DuplicateKeyException` 分支：

```java
} catch (DuplicateKeyException e) {
    String first = readIdempotentResultWithRetry(idemKey);
    if (first != null) {
        return new RegisterResponse(first);
    }
    throw new BusinessException(ErrorCode.CONFLICT.getCode(), "手机号已注册");
}
```

`readIdempotentResultWithRetry` 要求：

- 最多读取 **3 次**（含首次立即读取），相邻读取间隔 **50ms**，总等待 ≤100ms。
- 每次读取 `stringRedisTemplate.opsForValue().get(idemKey)`；取到非空值立即返回。
- `InterruptedException`：恢复中断标记（`Thread.currentThread().interrupt()`），记录 `log.warn("auth register idempotent reread interrupted")`，返回 `null`。
- 常量声明在 `AuthServiceImpl` 内（如 `IDEMPOTENT_REREAD_ATTEMPTS = 3`、`IDEMPOTENT_REREAD_INTERVAL_MILLIS = 50L`），禁止魔法值。
- 该重读**只**在 `DuplicateKeyException` 分支执行；其他异常不得吞掉或重试。
- 不引入 Redis SETNX、Redisson 锁、新依赖，不修改公共模块与事务注解。

### 5.3 日志

- 只允许记录 userId 或失败类别，例如 `log.info("auth register succeeded userId={}", userIdStr)`。
- 禁止记录 `Idempotency-Key`、手机号、密码、token、密码哈希。

## 6. 验收标准（可验证）

- [ ] 缺失 `Idempotency-Key` → `40000`，message 为“缺少 Idempotency-Key”。
- [ ] 非法格式（如 `not-a-uuid`、`1-1-1-1-1`）→ `40000`，message 为“Idempotency-Key 必须为 UUID”，且不查库、不写 Redis。
- [ ] 合法 UUID → 保持现有注册流程与响应不变。
- [ ] `INSERT` 抛 `DuplicateKeyException`，重读第 2 次取到 userId → 返回首次 userId，不抛 `40900`，`insert` 仅调用一次。
- [ ] `INSERT` 抛 `DuplicateKeyException`，3 次重读均为空 → `40900`，message 为“手机号已注册”。
- [ ] 行为与 `docs/contracts/api-conventions.md` §7、`auth-service.yaml` 的 register / 409 描述一致。
- [ ] 未修改契约、规则、ADR、父 pom、公共模块或其他服务。
- [ ] `mvn -pl atifah-services/auth-service -am verify` 通过。
- [ ] 日志不含 `Idempotency-Key`、手机号、密码、token。
- [ ] PR diff ≤400 行，分支 `fix/T-005-auth-register-idempotency-hardening`。

## 7. 测试要求

在 `AuthServiceImplTest` 中至少覆盖：

- [ ] `register_missingIdempotencyKey_throws40000`（保留现有用例）。
- [ ] `register_malformedIdempotencyKey_throws40000`：验证 `authAccountMapper.selectCount` 与 `insert` 均未被调用。
- [ ] `register_duplicateKey_retryFindsFirstResult_returnsUserId`：冲突分支内 `get` 第一次返回 `null`、第二次返回 userId；断言返回首次 userId、无异常。
- [ ] `register_duplicateKey_retryExhausted_throws40900`：冲突分支内 `get` 连续 3 次返回 `null`；断言 `40900`。
- [ ] 验证冲突分支内的重读次数：成功场景 `get` 调用 2 次、失败场景 3 次；若按整个方法统计，需再加上冲突前首次查询的 1 次。
- [ ] 现有注册成功、重复手机号、相同幂等键用例改用合法 UUID（当前测试使用的 `idem-1` 等不是 UUID，必须同步替换）。
- [ ] 有效 UUID 用例继续通过。

测试约束：

- 使用 Mockito mock `StringRedisTemplate` / `AuthAccountMapper`，不依赖真实 Redis / MySQL。
- 冲突用例最多引入约 100ms 实际等待，不得 flaky；不要用随机的 `Thread.sleep` 或依赖机器速度的断言。

## 8. 交付物

- [ ] 代码修改与单元测试
- [ ] PR（关联本任务包，分支 `fix/T-005-auth-register-idempotency-hardening`）
- [ ] 评审报告归档到 §11
- [ ] 不修改契约与规则

## 9. 自检命令

```bash
cd D:/Project/atifah
mvn -pl atifah-services/auth-service -am verify
git diff --check
git status
```

## 10. 风险与回滚

- **风险**：冲突分支最多增加 100ms 等待，且发生在数据库事务内（仅重复键冲突的罕见路径）；并发相同键仍不保证严格返回首次结果，已由契约措辞限定为串行重试保证。
- **回滚**：回退本次提交即可；无表结构、无数据迁移、无配置变更。
- **数据迁移**：无。

## 11. 评审与自测试记录

- 评审报告：`docs/review/reports/T-005-<head-short-sha>.md`

| 轮次 | 评审人 | 自测试命令/结果 | 结论 | Blocker/Major | 处理 |
| --- | --- | --- | --- | --- | --- |
| 1 | Codex |  |  |  |  |

## 12. 状态变更记录

| 日期 | 从 | 到 | 操作人 | 依据/证据 |
| --- | --- | --- | --- | --- |
| 2026-10-19 | — | draft | Claude | 由 T-001 Codex 报告遗留项创建；并发语义经维护者确认（ADR-0002 §5.1） |
