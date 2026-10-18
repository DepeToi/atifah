# AGENTS.md — microhoov-admin-web

> 模块级规则，与根 [`AGENTS.md`](../AGENTS.md) 叠加生效；冲突时以本文件为准（就近覆盖）。
> 工程说明见 [`README.md`](README.md)，前端硬规则见 [`frontend/AGENTS.md`](frontend/AGENTS.md)。

## 边界

- 前端只经网关 `/admin/api/**` 访问 `admin-service`，**禁止直连业务服务或数据库**。
- `backend/` 的 artifactId 是 `admin-service`，端口 8090，包根 `com.microhoov.admin`；BFF 只做聚合编排，不直连业务库。
- 管理端使用独立鉴权链（独立密钥 + MFA），不复用 C 端 Token；未接入前不要把登录逻辑写死在页面里。

## 前端硬规则

- 只通过 `src/api/http.ts` 的 `request<T>()` 发请求，不裸调 axios；新接口按域建 `src/api/<domain>.ts`，显式标注返回类型。
- 类型导入必须 `import type`（`verbatimModuleSyntax`）；`strict` + `noUnusedLocals` 全开，构建失败即不通过。
- 响应类型以 `src/api/types.ts` 为准，与 `common-core` 的 `Result` / `PageResult` 一致；改结构要同步契约、后端、前端三处。
- 样式用 `src/styles/index.css` 的 CSS 变量，不写死色值；类名 kebab-case；避免内联 `style`。
- 新增路由必须有 `meta.title`（顶栏与 `document.title` 都依赖它），组件一律懒加载。
- 敏感数据（手机号、情绪 / 量表）默认脱敏，禁止 `console` 输出。
- 新增 `VITE_*` 变量时同步 `src/vite-env.d.ts` 的 `ImportMetaEnv`。

## 当前阶段

- 页面为骨架，数据用 `--` 或 `<span class="badge">待接入</span>` 占位；接入接口后替换占位并移除徽标。
- 未引入 ESLint / Prettier，风格为手工约定：无分号、单引号、2 空格缩进、尾逗号。
