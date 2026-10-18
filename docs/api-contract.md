# 接口契约总纲（API Contract）

> 本文件是前后端接口的统一规则，是接口设计、实现、联调的唯一依据。
> 通用响应结构与错误码的代码权威在后端 `common-core`（`Result` / `PageResult` / `ErrorCode`），前端镜像在 `src/api/types.ts`；
> 本文件约束「接口怎么定」，具体每个接口的明细另按服务拆分到 `docs/api/` 下（骨架阶段逐步补充）。
> AI 每次必读的执行摘要见根 [`AGENTS.md`](../AGENTS.md) §5，本文件是细则；敏感字段与鉴权的执行细则见 [`.claude/rules/security.md`](../.claude/rules/security.md)。

## 目录

- [1. 适用与原则](#1-适用与原则)
- [2. 基础约定](#2-基础约定)
- [3. 统一响应](#3-统一响应)
- [4. 错误码](#4-错误码)
- [5. 鉴权](#5-鉴权)
- [6. 公共请求头](#6-公共请求头)
- [7. 幂等](#7-幂等)
- [8. 分页](#8-分页)
- [9. 路径与命名](#9-路径与命名)
- [10. 字段与脱敏](#10-字段与脱敏)
- [11. 明细文档模板](#11-明细文档模板)
- [12. 变更流程](#12-变更流程)

## 1. 适用与原则

- 适用于 C 端 `/api/v1/**` 与管理端 `/admin/api/**`（均由网关统一入口）。
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

## 3. 统一响应

成功与失败都用 `Result<T>`：

~~~json
{
  "code": 0,
  "message": "OK",
  "data": {},
  "timestamp": 1700000000000
}
~~~

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | int | 0 成功，非 0 失败（取值见错误码表） |
| message | string | 提示信息 |
| data | T | 业务数据，可为 null |
| timestamp | long | 服务端时间戳（毫秒） |

分页统一 `PageResult<T>`：

~~~json
{
  "records": [],
  "total": 100,
  "page": 1,
  "size": 20
}
~~~

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

业务错误**复用通用码**（40000 参数错误、40900 冲突等），用 message 说明具体原因，不新增业务专属码；确需新增通用码时才在 `ErrorCode` 枚举登记并同步前端 `types.ts`，禁止各服务硬编码数字。

## 5. 鉴权

- C 端：请求头 `Authorization: Bearer <access_token>`，由网关 `AuthGlobalFilter` 校验（当前 `microhoov.gateway.auth.enabled=false`，接入 auth-service 后开启）。
- 网关校验通过后写入下游请求头 `X-User-Id`、`X-Username`，业务服务直接读取，不重复验签。
- 管理端：独立鉴权链（独立密钥 + MFA），复用 `/admin/api` 前缀但走管理端 Token，待接入。
- 前端收到 401 统一清理登录态并跳转登录页。

## 6. 公共请求头

| 请求头 | 说明 | 是否必填 |
| --- | --- | --- |
| Authorization | Bearer Token（C 端） | 除白名单外必填 |
| Idempotency-Key | 幂等键 | 写接口必填 |
| X-Trace-Id | 链路追踪，网关生成/透传 | 可选 |

## 7. 幂等

- 所有写接口（POST / PUT / DELETE）必须要求客户端带 `Idempotency-Key`（UUID）。
- 服务端用 Redis（`CacheKeys.IDEMPOTENT`，24h）+ 数据库唯一索引双保险。
- 相同幂等键的重复请求返回首次结果，不产生副作用。

## 8. 分页

- 列表查询统一请求参数 `page`（从 1 开始）、`size`（默认 20，上限 100）。
- 响应统一 `PageResult<T>`。
- 分页插件 `PaginationInnerInterceptor` 已装配。

## 9. 路径与命名

- 资源用复数名词：`/api/v1/users`、`/api/v1/moods`。
- 子资源嵌套：`/api/v1/users/{userId}/moods`。
- 动作体现在 HTTP 方法上（GET 查询 / POST 创建 / PUT 更新 / DELETE 删除），路径不用动词。
- 版本号前缀 `v1`；破坏性变更升 `v2`，新增字段保持向后兼容。

## 10. 字段与脱敏

- 敏感字段默认脱敏返回：手机号 `138****5678`。
- 日记正文、量表结果等敏感数据加密存储，接口默认不返回明文；管理端查看明文需专项授权并记录审计。
- 日志禁止打印正文、手机号、密码，只打 ID / 状态 / 脱敏摘要。

## 11. 明细文档模板

每个服务的接口明细按 `docs/api/<service>.md` 拆分，统一用以下模板：

~~~markdown
### POST /api/v1/moods — 创建打卡

- 鉴权：需要
- 幂等：需要 Idempotency-Key

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| recordDate | string | 是 | 日期 yyyy-MM-dd |
| moodType | string | 是 | 情绪类型 |
| content | string | 否 | 正文（加密存储） |

响应：Result<MoodRecord>

错误码：40000 / 40100 / 40900
~~~

## 12. 变更流程

- 改接口先改本契约与对应 `docs/api/<service>.md`，再动代码，保证前后端以文档为准。
- 契约与 `common-core` 的 `Result` / `ErrorCode`、前端 `types.ts` 保持一致，任何一处变更需同步三处。
