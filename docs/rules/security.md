# 安全与隐私规则（SEC）

> 适用范围：网关、所有业务服务、管理后台 BFF 与前端。
> 违反 SEC-01 ~ SEC-03 的 PR 直接 `REQUEST_CHANGES`；规则编号与 `docs/review/standards.md` 一致。

## SEC-01 数据分级与处理

| 级别 | 数据 | 要求 |
| --- | --- | --- |
| 敏感个人信息 | 情绪日记正文、量表结果、语音/图片、匿名身份映射 | 字段级加密（AES-256-GCM），密钥由 KMS/Vault 管理；接口默认不返回明文 |
| 个人敏感字段 | 手机号、密码、设备标识 | 密码只存 BCrypt/Argon2id 哈希；手机号脱敏展示 |
| 一般业务数据 | 昵称、打卡分值、积分 | 按最小权限访问 |

- 禁止在日志、控制台、异常 message、MQ 消息、URL query 中出现上述敏感明文。
- 日志只打 ID / 状态 / 脱敏摘要；手机号格式 `138****5678`。
- 管理端查看明文必须专项授权并记录审计；匿名帖反查同样留痕。
- 数据导出/注销走 Saga 清理各服务数据，不得只删主表。
- 需字段级加密的「敏感个人信息」列名使用 `*_cipher`（如 `content_cipher`），禁止明文列；手机号属「个人敏感字段」，按 ADR 0003 明文存储并脱敏展示。

## SEC-02 密钥与凭据

- 禁止硬编码密码、JWT 密钥、Token、AK/SK、内部域名、数据库连接串。
- `JWT_SECRET` 只允许通过环境变量注入；仓库默认值仅限本地开发，生产必须替换为至少 256 位随机值或改用 RS256。
- 新增配置项一律使用 `${ENV:default}` 形式；默认值不得是真实生产凭据。
- 密钥、证书、`.env`、`*.local.yml` 不得提交 Git。

## SEC-03 认证与鉴权

- 唯一入口是网关：C 端 `/api/v1/**`，管理端 `/admin/api/**`，WebSocket `/ws/**`。
- 网关校验 JWT 后写入 `X-User-Id` / `X-Username`；业务服务直接读 `CommonConstants`，**不得重复验签**。
- 业务服务与管理端不得绕过网关直连；管理端不得直连业务库。
- 管理端使用独立鉴权链（独立密钥 + MFA + IP 白名单），不复用 C 端 Token。
- 网关鉴权开关 `atifah.gateway.auth.enabled` 默认 `false`；开启前必须补齐白名单（当前仅 `/api/v1/auth/`、`/actuator/`，`/ws/**`、`/admin/api/**` 会被 401）。
- 401 统一清理登录态并跳转登录页；403 不得泄露资源是否存在。

## SEC-04 写入幂等

- 业务写接口（POST / PUT / DELETE）必须校验 `Idempotency-Key`（`CommonConstants.HEADER_IDEMPOTENCY_KEY`）。
- 认证类接口例外：登录、刷新、登出不强制；注册必须带。详见 `docs/contracts/api-conventions.md` §7。
- 服务端用 Redis `CacheKeys.IDEMPOTENT`（24h）+ 数据库唯一索引双保险。
- `Idempotency-Key` 的格式、串行/并发语义与错误码以 `docs/contracts/api-conventions.md` §7 为准，本文件不重复阈值与实现细节。

## SEC-05 传输、CORS、上传与防刷

- 生产全站 HTTPS/TLS 1.3 + HSTS；数据库账号最小权限，禁止用 root 连接生产库。
- CORS 由网关统一处理；`CORS_ALLOWED_ORIGINS` 默认 `*` 仅限本地，生产必须收敛为具体域名。
- 文件上传校验类型、大小、后缀；远程抓取配置 SSRF 白名单。
- 防刷按需启用验证码、UID/IP 频控、设备指纹、行为风控；公开内容先审后发。
- 新增依赖先确认；不得为一个工具方法引入新库，禁止引入第二套 JSON / 日志 / HTTP 客户端。

## 评审提示

- Codex 评审时优先检查：日志与异常中的敏感字段、硬编码密钥、绕过网关、幂等键缺失。
- 仅发现“可能存在风险”但无法确认时，标记 `[Question]`，不要直接判 `Blocker`；确认后可升级。
