# 接口契约总纲（API Contract）

> 本文件是前后端接口的统一规则，是接口设计、实现、联调的唯一依据。
> 通用响应结构与错误码的代码权威在后端 `common-core`（`Result` / `PageResult` / `ErrorCode`），前端镜像在 `src/api/types.ts`。
> 各服务的具体接口明细放 `docs/contracts/openapi/<service>.yaml`。

## 1. 适用与原则

- 适用于 C 端 `/api/v1/**` 与管理端 `/admin/api/**`，均由网关统一入口。
- REST 风格 + JSON 通信，UTF-8。
- 统一响应 `Result` / `PageResult`，禁止各服务自定义响应壳。
- 一服务一库，跨服务数据经 API 或 MQ 获取，不在接口里跨库聚合。

## 2. 基础约定

| 项 | 约定 |
| --- | --- |
| 基路径 | C 端 `/api/v1`，管理端 `/admin/api` |
| Content-Type | `application/json; charset=UTF-8` |
| 时间 | `yyyy-MM-dd HH:mm:ss`，时区 `Asia/Shanghai` |
| ID | 一律 `string`（雪花 ID 超出 JS Number 精度，后端序列化为字符串） |
| 字段命名 | JSON 用 camelCase（与后端 Jackson / MyBatis 下划线转驼峰一致） |
| 资源命名 | 复数名词；动作交给 HTTP 方法；路径 kebab-case |

## 3. 统一响应

成功与失败都用 `Result<T>`：

```json
{
  "code": 0,
  "message": "OK",
  "data": {},
  "timestamp": 1700000000000
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | int | 0 成功，非 0 失败（取值见错误码表） |
| message | string | 提示信息 |
| data | T | 业务数据，可为 null |
| timestamp | long | 服务端时间戳（毫秒） |

分页统一 `PageResult<T>`：

```json
{
  "records": [],
  "total": 100,
  "page": 1,
  "size": 20
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| records | T[] | 当前页数据 |
| total | long | 总条数 |
| page | long | 页码，从 1 开始 |
| size | long | 每页条数 |

## 4. 错误码

统一取 `ErrorCode` 枚举，HTTP 状态由 `GlobalExceptionHandler` 映射：

| code | 含义 | HTTP |
| --- | --- | --- |
| 0 | 成功 | 200 |
| 40000 | 参数错误 | 400 |
| 40100 | 未认证或登录过期 | 401 |
| 40300 | 无权限 | 403 |
| 40400 | 资源不存在 | 404 |
| 40500 | 方法不支持 | 405 |
| 40900 | 数据冲突 | 409 |
| 42900 | 请求过于频繁 | 429 |
| 50000 | 系统繁忙 | 500 |

- 业务错误**复用通用码**（40000 参数错误、40900 冲突等），用 message 说明具体原因，不新增业务专属码。
- 确需新增通用码时才在 `ErrorCode` 枚举登记，并同步前端 `types.ts`；禁止各服务硬编码数字。

## 5. 鉴权

- C 端：请求头 `Authorization: Bearer <access_token>`，由网关 `AuthGlobalFilter` 校验（当前 `atifah.gateway.auth.enabled=false`，接入 auth-service 后开启）。
- 网关校验通过后写入下游请求头 `X-User-Id`、`X-Username`，业务服务直接读取，不重复验签。
- 管理端：独立鉴权链（独立密钥 + MFA + IP 白名单），复用 `/admin/api` 前缀但走管理端 Token，待接入。
- 前端收到 401 统一清理登录态并跳转登录页。

## 6. 公共请求头

| 请求头 | 说明 | 是否必填 |
| --- | --- | --- |
| Authorization | Bearer Token（C 端） | 除白名单外必填 |
| Idempotency-Key | 幂等键（UUID） | 写接口必填 |
| X-Trace-Id | 链路追踪，网关生成/透传 | 可选 |

## 7. 幂等

- 业务写接口（POST / PUT / DELETE）必须要求客户端带 `Idempotency-Key`。
- 认证类接口例外：登录、刷新、登出不强制 `Idempotency-Key`（登录有失败计数，刷新轮换本身防重放，登出是幂等的）；注册仍必须带，用于防止重复开户。
- 服务端用 Redis（`CacheKeys.IDEMPOTENT`，24h）+ 数据库唯一索引双保险。
- `Idempotency-Key` 必须为规范 UUID（`8-4-4-4-12`）；缺失或格式非法返回 `40000`。
- 相同幂等键 + 相同请求体的**串行重复请求**返回首次结果，不产生副作用。
- 相同幂等键 + 不同请求体：串行请求返回 `40900`，客户端不得复用同一幂等键提交不同 payload。
- **并发**保证仅适用于会命中同一业务唯一键的请求（例如相同手机号注册）：由数据库唯一索引兜底；冲突方在唯一键冲突后**有界短暂重读**幂等结果（最多 3 次、间隔 50ms，总等待 ≤100ms），取到则返回首次结果，否则返回 `40900`。不引入 Redis 原子占位。同一幂等键的不同 payload 并发不在 v0.1 保证范围。

## 8. 分页

- 列表查询统一请求参数 `page`（从 1 开始）、`size`（默认 20，上限 100）。
- 响应统一 `PageResult<T>`；禁止无界查询与大偏移分页。

## 9. 字段与脱敏

- 敏感字段（情绪日记正文、量表结果、手机号）默认不返回明文；确需返回时按 `docs/rules/security.md` 授权并审计。
- 手机号展示脱敏为 `138****5678`。
- 金额/计数使用整数或定点小数，禁止浮点。

## 10. 变更流程

1. 先改本总纲或对应 `openapi/<service>.yaml`，再改实现。
2. 破坏性变更必须新增版本路径（`/api/v2`）或保持旧字段兼容。
3. 契约 PR 必须由 Claude 发起并单独提交；ADR 按 `docs/review/standards.md` DOC-01 分级（首次冻结、破坏性变更、跨服务语义、新组件必需；兼容新增只需变更说明与契约 diff）。功能 PR 不得夹带契约修改。
4. 同步修改：`common-core` 的 `Result` / `ErrorCode`、前端 `src/api/types.ts`、相关测试。
