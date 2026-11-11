# 0003. auth v0.1 认证基线：令牌、会话与手机号

- 状态：已采纳
- 日期：2026-11-11
- 决策人：项目维护者

## 背景

P0 需要用 auth 纵向切片跑通 Claude → CodeBuddy → Codex → 人工的完整闭环。网关鉴权当前关闭，auth 是解锁后续链路的前置。

现有骨架已提供 JWT 签发（HS256、15 分钟）、BCrypt、Refresh Token TTL 7 天与登录失败计数配置，但没有认证契约与表结构。同时存在三处需要拍板的取舍：Refresh Token 如何存储与撤销、手机号是否加密落库、注册是否要求短信验证码。

原架构表述（OAuth2.1、`jti` 黑名单、手机号加密）与 P0 的最小闭环存在差异；本次同时把 `docs/architecture.md` 调整为 v0.1 的实际边界，长期演进方向保留在本 ADR 中。若不明确 v0.1 边界，契约、DDL 与实现会各自理解，冻结后返工成本更高。

## 决策

1. **接口范围**：v0.1 只提供 `/api/v1/auth/register`、`login`、`refresh`、`logout` 四个第一方 REST 接口；不做完整 OAuth2.1 授权服务器，不接入短信验证码与三方登录。
2. **Access Token**：JWT、HS256、有效期 15 分钟；claims 为 `iss`、`sub`（userId）、`username`（脱敏手机号）、`iat`、`exp`。v0.1 不引入 `jti` 与 Redis 黑名单，登出后 Access Token 最长在剩余 TTL 内仍有效；生产切换 RS256 不在本版范围。
3. **Refresh Token**：不透明随机令牌（`SecureRandom` 256 位，Base64URL），数据库只存 SHA-256 十六进制哈希；有效期 7 天（每次轮换按新签发时间重新计算），每次刷新轮换（旧令牌立即撤销）。轮换在单事务内完成，撤销使用条件更新（仅 `revoked_at IS NULL` 时成功），并发刷新同一令牌只有一个请求成功。已撤销令牌在 30 秒宽限期内再次使用只返回 `40100`；超过宽限期再次使用视为泄露，撤销该用户全部有效刷新令牌。登出按请求体中的 Refresh Token 撤销，重复登出幂等。
4. **存储与真相源**：会话状态以 `t_auth_refresh_token` 为唯一真相源；Redis 只用于登录失败计数（`CacheKeys.AUTH_LOGIN_FAIL`），不存 Refresh Token。现有 `CacheKeys.AUTH_REFRESH` 与本模型不一致，由后续 Arch 任务清理，本版不引用。
5. **手机号**：按 SEC-01 的「个人敏感字段」处理，明文存储于 `t_auth_account.phone` 并建唯一索引；接口与日志必须脱敏（`138****5678`）。v0.1 不做落库加密；后续若合规要求加密，走 `*_cipher` + 检索哈希迁移并新增 ADR。
6. **密码**：只存 BCrypt 哈希（复用 `common-security` 的 `PasswordEncoder`）；密码策略为 8–64 位可打印 ASCII 字符（不含空格）且至少包含字母与数字，避免 BCrypt 72 字节截断。
7. **登录失败**：同一手机号 10 分钟内连续失败 5 次后返回 `42900`，窗口结束自动解除；成功登录后计数清零。账号禁用仅在密码校验通过后返回 `40300`，凭据错误统一返回 `40100`，避免账号枚举。
8. **注册幂等**：注册必须携带 `Idempotency-Key`，只返回全局 `userId`，不返回令牌；客户端随后调用登录接口。登录、刷新、登出不要求幂等键。
9. **用户 ID**：v0.1 由 auth-service 用雪花算法生成全局 `userId`；user-service 的资料初始化在后续契约中定义（事件或首次访问懒创建）。
10. **网关鉴权开启条件**：`atifah.gateway.auth.enabled` 保持 `false`，直到同时满足：（1）auth 四个接口联调通过且契约冻结；（2）网关与 auth-service 使用同一 `JWT_SECRET`（生产至少 256 位随机值）；（3）白名单覆盖 `/api/v1/auth/` 与 `/actuator/`，并明确 `/ws/**`、`/admin/api/**` 的独立鉴权方案（管理端不复用 C 端 Token）；（4）受保护路由的 401 行为有集成测试。开启后由网关写入 `X-User-Id` / `X-Username`，下游服务不得重复验签。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| Refresh Token 用不透明随机串 + DB 哈希 | 可撤销、可轮换、DB 为真相源，泄露面小 | 每次刷新一次写库 | 采纳 |
| 双 JWT + Redis 黑名单 | 写库少、无状态 | 关键会话状态落在缓存，撤销依赖 Redis 可用性，与 DB-05 冲突 | 否决 |
| 仅 Access Token，不做刷新 | 实现最简单 | 15 分钟后需重新登录，与现有配置和架构不符 | 否决 |
| 手机号明文 + 唯一索引 + 脱敏 | 与现有 V1 一致，登录检索简单 | 数据库备份与运维侧可见手机号 | 采纳 |
| 手机号密文 + HMAC 检索哈希 | 落库不出现明文 | 需新增密钥管理与加解密能力，P0 范围扩大 | 否决（后续按合规需要再迁） |
| 注册要求短信验证码 | 防刷与号码真实性更好 | 需短信网关、验证码表与新接口，P0 范围显著扩大 | 否决（上线前必须补齐） |
| 完整 OAuth2.1 授权服务器 | 标准、可支持三方客户端 | 与首方 App 的 P0 闭环不匹配，成本高 | 否决（长期演进方向） |

## 影响

- 正面：契约边界清晰；Refresh Token 可撤销、可审计；DB 是会话唯一真相源；P0 可在无短信通道的前提下跑通。
- 负面/代价：
  - 手机号明文落库，数据库备份与运维账号成为额外风险面，必须依赖最小权限与审计。
  - v0.1 注册无验证码，存在批量注册与号码伪造风险，**不得直接上线**。
  - 登出后 Access Token 在剩余 TTL（≤15 分钟）内仍有效；`jti` 黑名单列为后续增强。
  - 登录失败按手机号计数，存在被恶意触发锁定的风险；v0.1 仅靠服务限流兜底，后续补充 IP/设备维度。
  - 未实现设备管理、三方登录、密码找回与 OAuth2.1。
- 需要同步修改的文档与代码：
  - `docs/contracts/openapi/auth-service.yaml`、`docs/contracts/db/auth-service.sql`
  - `docs/architecture.md` §7（认证与会话、手机号存储表述）
  - `AGENTS.md` §1/§4（手机号数据分级与 SEC 摘要）
  - `docs/review/standards.md` SEC-01（评审口径）
  - `docs/rules/security.md` SEC-01、`docs/rules/database.md` DB-03（`*_cipher` 适用范围说明）
  - `docs/contracts/README.md` §4（敏感字段检查项改为“加密或脱敏”）
  - `docs/db.md` §2（认证表清单）、`README.md`（总体架构一句话概括）
  - `TODO.md` P0-3 验收口径（敏感字段处理按本 ADR）
  - `docs/adr/README.md` 索引
  - 后续任务包 T-001（实现约束与测试要求）
- 回滚方式：回退契约与 ADR；`t_auth_refresh_token` 在 v0.1 无存量数据，可直接删除；恢复架构与规则原文。

## 关联

- 相关契约：`docs/contracts/openapi/auth-service.yaml`、`docs/contracts/db/auth-service.sql`
- 相关任务包：T-001（待创建）
- 替代/被替代的 ADR：无
