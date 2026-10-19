# AGENTS.md — atifah-admin-web

> 模块级规则，与根 [`AGENTS.md`](../AGENTS.md) 及 [`docs/rules/*`](../docs/rules/) 叠加生效；冲突时以本文件为准（就近覆盖）。
> 工程说明见 [`README.md`](README.md)，前端硬规则见 [`frontend/AGENTS.md`](frontend/AGENTS.md)。

## 边界

- 前端只经网关 `/admin/api/**` 访问 `admin-service`，**禁止直连业务服务或数据库**。
- `backend/` 的 artifactId 是 `admin-service`，端口 8090，包根 `com.atifah.admin`；BFF 只做聚合编排，不直连业务库。
- 管理端使用独立鉴权链（独立密钥 + MFA + IP 白名单），不复用 C 端 Token；未接入前不要把登录逻辑写死在页面里。
- 涉及管理端接口的变更先改 `docs/contracts/`，再改 `admin-service` 与前端类型。

## 前端规则

前端硬规则、工程结构与当前阶段统一见 [`frontend/AGENTS.md`](frontend/AGENTS.md)；本文件只保留管理端边界与自检。

## 自检

```bash
cd frontend && npm run type-check && npm run build
mvn -pl atifah-admin-web/backend -am verify
```
