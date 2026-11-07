# AGENTS.md — atifah-gateway

> 模块级规则，与根 [`AGENTS.md`](../AGENTS.md) 及 [`docs/rules/*`](../docs/rules/) 叠加生效；冲突时以本文件为准（就近覆盖）。
> 网关是唯一入口，安全问题按 [`security.md`](../docs/rules/security.md) 执行。

## 运行环境

- 响应式 Netty + Spring Cloud Gateway，端口 8080，`spring.main.web-application-type: reactive`。
- 依赖边界见根 `AGENTS.md` §3：只依赖 `common-core` + `common-security`，**禁止**引入 `common-web` / `common-mybatis` / `common-redis` / `spring-boot-starter-web`（会拉起 servlet 栈导致启动失败）。
- 禁止开启 `spring.threads.virtual.enabled`。

## 代码约束

- 本模块归 Claude（架构角色）维护；CodeBuddy 不直接修改网关代码，需要变更时回到 Claude。
- 过滤器实现 `GlobalFilter` + `Ordered`；链路上禁止任何阻塞调用（JDBC、同步 HTTP、`Thread.sleep`），必须返回 `Mono` / `Flux`。
- 需要直接写响应体时保持与 `Result` 一致（`{code,message,data,timestamp}`），不要引入 servlet 侧的序列化工具。
- 网关没有 `@RestControllerAdvice`（无 servlet 上下文），异常在过滤器内自行转成 `Result` 并设置状态码。
- 新增路由：在 `application.yml` 的 `spring.cloud.gateway.server.webflux.routes` 追加 `lb://atifah-<service>`，并同步 `README.md` 端口表与相关契约/文档。
- 鉴权开关 `atifah.gateway.auth.enabled` 默认 `false`。开启前必须补齐白名单（现状只有 `/api/v1/auth/`、`/actuator/`，`/ws/**` 与 `/admin/api/**` 会被 401）。
- 2026-10-29 已迁移至
  `spring-cloud-starter-gateway-server-webflux`（key 同步迁移到 `spring.cloud.gateway.server.webflux.*`）；
  `spring-cloud-alibaba-sentinel-gateway` 上的 exclusion 用于阻断 SCA 2025.0.0.0 的经典 starter 回流，
  升级 SCA 2025.1.0.0（随 Boot 4 平台迁移，另立项目）后移除。网关 actuator 端点（/actuator/gateway/**）
  在 SCG 4.3.5 上始终未注册（迁移前即如此），如需路由巡检用逐路径探测代替。

## 契约

- 路由前缀与 `docs/contracts/api-conventions.md` 保持一致：C 端 `/api/v1/**`、管理端 `/admin/api/**`、WebSocket `/ws/**`。
- 网关只做鉴权、限流、TraceId、路由，不写业务逻辑；不在网关里做数据聚合。
- 修改鉴权、CORS、白名单属于架构/安全变更，先更新 ADR 或规则，再由 Claude 执行。

## 自检

```bash
mvn -pl atifah-gateway -am verify
```
