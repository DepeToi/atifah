---
id: T-001
title: auth 手机号注册
status: done
owner: codebuddy
created: 2026-11-11
updated: 2026-11-11
status_reason: PR #7 已合并，CI 与 Codex 评审通过
contract_baseline: arch-baseline-v0.1
related_adr: [docs/adr/0003-auth-baseline.md]
---

# T-001 auth 手机号注册

## 1. 目标与非目标

- 一句话目标：按契约实现 `POST /api/v1/auth/register`，完成手机号注册、BCrypt 密码哈希、注册幂等与统一错误响应。
- 业务背景：auth v0.1 纵向切片的第一个任务，用于跑通 P0 协作流程。为满足单 PR 语义 diff ≤400 行，本任务只覆盖注册；登录、刷新、登出拆到后续任务。
- 关联需求 / Issue：`TODO.md` P0-7。
- 关联 ADR：`docs/adr/0003-auth-baseline.md`（手机号明文、BCrypt、v0.1 无短信验证码）。
- 明确不做：登录、Token 签发、刷新、登出、短信验证码、三方登录、user-service 资料初始化、网关鉴权开启、密码找回。

## 2. 上下文与必读（只列路径）

- 规则：`AGENTS.md`、`docs/rules/security.md`、`docs/rules/code-style.md`、`docs/rules/database.md`、`atifah-services/AGENTS.md`
- 契约：`docs/contracts/openapi/auth-service.yaml`（operationId: `register`）、`docs/contracts/db/auth-service.sql`（表 `t_auth_account`）、`docs/contracts/api-conventions.md` §7
- 现有实现 / 测试：`atifah-services/auth-service/src/main/java/com/atifah/auth/AuthServiceApplication.java`、`src/main/resources/db/migration/V1__init.sql`；当前无测试
- 公共能力：`common-core`（`Result` / `ErrorCode` / `BusinessException` / `CommonConstants`）、`common-security`（`PasswordEncoder`）、`common-redis`（`CacheKeys`）、`common-mybatis`（`BaseEntity`）
- 关联 ADR：`docs/adr/0003-auth-baseline.md`

## 3. 变更范围

### 允许修改

```text
atifah-services/auth-service/**
docs/tasks/T-001-auth-register.md   # 仅 front matter（status / updated / status_reason）
```

> 本任务不需要新增依赖；如发现必须新增，停止实现并在 PR 中标记 `[Arch]` 回到 Claude。

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

- 分层：`controller` / `service` + `service.impl` / `mapper` / `entity` / `dto` / `vo`；构造注入 + `@RequiredArgsConstructor`，禁止字段 `@Autowired`。
- 实体 `AuthAccount extends BaseEntity`：字段 `userId`、`phone`、`passwordHash`、`status`、`lastLoginAt`；`id` / `createdAt` / `updatedAt` / `deleted` 继承。不要修改 V1 迁移。
- Mapper `AuthAccountMapper`：不继承 `BaseMapper`，只声明 `selectByPhone`、`insert`；SQL 全部写 `src/main/resources/mapper/AuthAccountMapper.xml`，显式列名、`#{}`、查询带 `deleted = 0`，insert 显式写入 `id` / `user_id` / `phone` / `password_hash` / `status`，时间与 `deleted` 交给数据库默认值。
- DTO `RegisterRequest`：record + Jakarta Validation；手机号 `^1[3-9][0-9]{9}$`；密码 8–64 位可打印 ASCII（不含空格），至少含字母与数字；校验消息用中文。
- Service `AuthService` / `AuthServiceImpl`：`register(RegisterRequest, String idempotencyKey)` 返回 `RegisterResult`。
  - 注册是单条 INSERT，**不要加 `@Transactional`**，避免事务内做 Redis 远程调用（DB-04）。
  - 流程：校验幂等键 → 查 Redis 幂等结果 → 查手机号是否已存在 → 生成 `id` 与 `userId`（`IdWorker.getId()`）→ BCrypt 编码 → INSERT → 写 Redis 幂等结果 → 返回 `userId`。
  - 手机号已存在：`BusinessException(ErrorCode.CONFLICT, "手机号已注册")`。
  - INSERT 捕获 `DuplicateKeyException`（并发兜底）：按 `api-conventions.md` §7 最多重读幂等结果 3 次、间隔 50ms、总等待 ≤100ms；取到且手机号匹配则返回首次 `userId`，否则返回 `40900`。
  - 内部错误不要抛 `BusinessException(ErrorCode.INTERNAL_ERROR)`：`common-web` 的 `resolveStatus` 未映射 50000，会落到 400；让 `IllegalStateException` / `DataAccessException` 冒泡到兜底 handler 返回 500。
  - 日志只允许 `userId` / 结果状态；禁止打印手机号、密码、请求体。
- 幂等组件 `AuthIdempotencyGuard`（`service` 包内，局部常量 `private static final`，不新增公共常量）：
  - 校验 `Idempotency-Key` 为规范 UUID（8-4-4-4-12）格式；缺失或非法抛 `BusinessException(ErrorCode.BAD_REQUEST, "Idempotency-Key 缺失或格式非法")`。
  - Redis Key：`String.format(CacheKeys.IDEMPOTENT, "auth:register", idempotencyKey)`；TTL 24 小时。
  - Value：`sha256(phone) + ":" + userId`；重放时手机号哈希不一致返回 `40900`，避免同一幂等键被不同请求复用。
  - Redis 不可用时直接失败（500），不降级为“只靠唯一索引”，避免破坏幂等语义；异常不要吞掉。
- Controller `AuthController`：`@RestController` + `@RequestMapping("/api/v1/auth")`；`@PostMapping("/register")`；`@Valid @RequestBody`；`Idempotency-Key` 用 `@RequestHeader(required = false)` 接收并在 Controller 做非空校验（缺失时抛 `40000`，不要依赖 `MissingRequestHeaderException` 的默认 500）。Controller 不写业务逻辑、不 try-catch。
- VO `RegisterResult`：`userId` 以字符串返回（雪花 ID 超出 JS Number 精度）。
- 不新增依赖：`spring-boot-starter-test` 已由根 POM 提供；Redis 用 `StringRedisTemplate`。

## 5. 验收标准（可验证）

- [ ] 成功：合法手机号 + 密码 + 规范 `Idempotency-Key` 返回 `code=0`、`data.userId` 为非空字符串。
- [ ] 参数：手机号格式错误、密码不满足策略、请求体缺失 → `40000` / HTTP 400。
- [ ] 幂等键：缺失或非规范 UUID → `40000` / HTTP 400；相同键 + 相同手机号的串行重放返回首次 `userId`，且只插入一条账号记录。
- [ ] 幂等键复用：相同键 + 不同手机号 → `40900`。
- [ ] 重复注册：手机号已存在且无匹配幂等记录 → `40900` / HTTP 409。
- [ ] 并发注册：唯一索引兜底；并发失败方在限定重读内取到首次结果则返回成功，否则 `40900`。
- [ ] 密码只存 BCrypt 哈希；日志、异常 message 中无手机号/密码明文。
- [ ] SQL 无 `SELECT *`、无拼接、查询带 `deleted = 0`；V1 迁移未修改。
- [ ] 只修改允许路径，未新增依赖。
- [ ] `mvn -pl atifah-services/auth-service -am test` 与 `mvn -o -q -DskipTests compile` 通过。

## 6. 测试与自检

- 验证级别：A（Feature + 安全相关）。
- 单元测试（Mockito，不依赖 MySQL/Redis/Nacos）：
  - `AuthServiceImplTest`：成功、幂等重放、幂等键复用冲突、手机号已存在、并发 `DuplicateKeyException` 重读成功/失败、Redis 异常向上抛出；断言密码编码后入库、日志不暴露敏感字段。
  - `AuthControllerTest`：`MockMvcBuilders.standaloneSetup` + `GlobalExceptionHandler`；成功 200/`code=0`、缺 `Idempotency-Key` 400/`40000`、参数错误 400/`40000`、重复手机号 409/`40900`。
  - `AuthIdempotencyGuardTest`：UUID 校验、Key 构造、TTL、同手机号重放、不同手机号冲突。
- 如本地中间件已启动，可补充手工验证；非 CI 必需：
  ```bash
  curl -X POST http://localhost:8080/api/v1/auth/register \
    -H 'Content-Type: application/json' \
    -H 'Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000' \
    -d '{"phone":"13800005678","password":"Passw0rd123"}'
  ```
- 自检命令：

```bash
mvn -pl atifah-services/auth-service -am test
mvn -o -q -DskipTests compile
git diff --check
```

## 7. 风险与回滚

- 风险：
  - 注册依赖 Redis 做幂等；Redis 不可用时请求失败（500），不静默降级。
  - INSERT 成功但 Redis 幂等写入失败的边缘情况下，同键重试可能返回 `40900`；唯一索引保证不会重复开户，需在 PR 中说明该限制。
  - v0.1 无短信验证码、手机号明文存储，均为 ADR 0003 的已知缺口。
  - 登录/刷新/登出不在本任务，契约中的相应接口暂不可用。
- 回滚方式：revert 本 PR；本任务不新增表、不修改 V1，无数据迁移。若手工验证产生测试账号，按手机号清理即可。
- 数据迁移影响：无。
