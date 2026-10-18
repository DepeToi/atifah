# AGENTS.md — microhoov-gateway

> 模块级规则，与根 [`AGENTS.md`](../AGENTS.md) 及 [`.claude/rules/*`](../.claude/rules/) 叠加生效；冲突时以本文件为准（就近覆盖）。
> 已知坑与排查见 [`docs/pitfalls.md`](../docs/pitfalls.md)。

## 运行环境

- 响应式 Netty + Spring Cloud Gateway，端口 8080，`spring.main.web-application-type: reactive`。
- 项目内公共模块只依赖 `common-core` + `common-security`（其余为 actuator / prometheus / Nacos / Sentinel / LoadBalancer）。**禁止**引入 `common-web` / `common-mybatis` / `common-redis` / `spring-boot-starter-web`——会拉起 servlet 栈导致启动失败。
- 禁止开启 `spring.threads.virtual.enabled`。

## 代码约束

- 过滤器实现 `GlobalFilter` + `Ordered`；链路上禁止任何阻塞调用（JDBC、同步 HTTP、`Thread.sleep`），必须返回 `Mono` / `Flux`。
- 需要直接写响应体时保持与 `Result` 一致（`{code,message,data,timestamp}`），不要引入 servlet 侧的序列化工具。
- 网关没有 `@RestControllerAdvice`（无 servlet 上下文），异常在过滤器内自行转成 `Result` 并设置状态码。
- 新增路由：在 `application.yml` 的 `spring.cloud.gateway.routes` 追加 `lb://microhoov-<service>`，并同步 `docs/module-layout.md` 与根 `README.md` 的端口表。
- 鉴权开关 `microhoov.gateway.auth.enabled` 默认 `false`。开启前必须补齐白名单（现状只有 `/api/v1/auth/`、`/actuator/`，`/ws/**` 与 `/admin/api/**` 会被 401）。
- 已弃用 `spring-cloud-starter-gateway` 但暂时保留（Sentinel 网关适配器依赖经典 starter），不要"顺手升级"。
