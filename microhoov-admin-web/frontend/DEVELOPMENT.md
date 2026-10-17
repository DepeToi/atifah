# 前端开发规范（microhoov-admin-web/frontend）

> 本文档描述管理后台前端 `microhoov-admin-web/frontend` 的工程结构与开发约定。
> 骨架阶段说明与请求链路见上级 [`microhoov-admin-web/README.md`](../README.md)。

## 1. 技术栈

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| Vue | ^3.5 | Composition API + `<script setup>` |
| TypeScript | ~5.9 | 严格模式（strict: true） |
| Vite | ^8.3 | 构建与开发服务器 |
| Element Plus | ^2.14 | 组件库（中文 locale） |
| Pinia | ^3.0 | 状态管理 |
| Vue Router | ^4.6 | history 路由 |
| Axios | ^1.20 | HTTP 客户端 |

## 2. 目录结构

```text
frontend/
├── .env.development        # 开发环境变量
├── .env.production         # 生产环境变量
├── index.html              # 入口 HTML（lang=zh-CN，robots noindex）
├── package.json
├── tsconfig.json           # strict + 路径别名 @/*
├── vite.config.ts          # 端口 5173、/admin/api 代理、@ 别名
├── public/favicon.svg
└── src/
    ├── main.ts             # 挂载 Pinia / Router / Element Plus(zh-cn) / 全局样式
    ├── App.vue             # 仅 <RouterView />
    ├── api/
    │   ├── http.ts         # axios 实例 + request<T>() 解包
    │   └── types.ts        # Result<T> / PageResult<T> / RESULT_SUCCESS_CODE
    ├── layouts/
    │   └── AdminLayout.vue # 侧边栏 + 顶栏 + 内容区 + 页脚
    ├── router/index.ts     # 路由表 + meta.title/subtitle + document.title
    ├── styles/index.css    # 设计变量 + 全局样式 + 响应式
    ├── views/              # 各页面（XxxView.vue）
    └── vite-env.d.ts       # ImportMetaEnv 类型声明
```

## 3. 常用命令

```bash
npm install        # 安装依赖（Node ^20.19.0 || >=22.12.0）
npm run dev        # 开发服务器 :5173，代理 /admin/api -> 网关 :8080
npm run type-check # 仅 vue-tsc 类型检查（不产出）
npm run build      # 类型检查 + 构建 -> dist/
npm run preview    # 本地预览构建产物
```

`npm run build` 已经包含 `vue-tsc --noEmit`，提交前务必保证类型检查通过。

## 4. TypeScript 规范

- 严格模式全开：`strict`、`noUnusedLocals`、`noUnusedParameters`、`noFallthroughCasesInSwitch`。未使用变量/参数会直接编译失败，不要留死代码。
- `verbatimModuleSyntax` 已开启：类型导入必须用 `import type`，否则构建报错。

  ```ts
  import type { Result, PageResult } from '@/api/types'   // 正确：类型导入
  import { request } from '@/api/http'                     // 正确：值导入
  ```

- 路径别名 `@/*` → `src/*`，同时配置在 `tsconfig.json`（paths）与 `vite.config.ts`（resolve.alias）两处，新增或调整时两处都要改。
- 共享响应类型（Result / PageResult）放 `src/api/types.ts`；业务模型按域拆分。
- 新增 `VITE_*` 环境变量时，同步在 `src/vite-env.d.ts` 的 `ImportMetaEnv` 接口里声明类型。

## 5. 代码风格（当前实际约定）

现有代码一致遵循：无分号、单引号、2 空格缩进、尾逗号。

```ts
import { ref } from 'vue'

const activeTab = ref('all')

const statusOptions = [
  { label: '正常', value: 'active' },
  { label: '已禁用', value: 'disabled' },
]
```

目前仓库尚未引入 ESLint / Prettier，以上是手工约定，后续可补一套 lint 配置固化（见「下一步」）。

## 6. 组件规范

- 统一使用 `<script setup lang="ts">`，单文件组件顺序为 script → template（需要时再加 style）。
- 视图组件命名 `XxxView.vue`（PascalCase），放 `src/views/`；布局放 `src/layouts/`。
- 响应式数据优先 ref / reactive；纯展示的静态常量用普通数组/对象即可（参见 `DashboardView.vue` 的 stats、quickLinks）。
- 组件通信 props/emits 用类型化的 defineProps / defineEmits。
- 无障碍：保持语义化标签与 aria 属性（现有代码已用 aria-label / aria-hidden）。
- 装饰性图标统一来自 `@element-plus/icons-vue`，用 `<el-icon>` 包裹。

## 7. 路由规范

- 路由集中在 `src/router/index.ts`，页面组件全部懒加载 `() => import('@/views/XxxView.vue')`。
- 所有页面嵌套在 AdminLayout 下（根路径 `/` 重定向到 `/dashboard`）；404 用 `/:pathMatch(.*)*` 兜底。
- 每个路由必须带 `meta.title`（顶栏标题 + document.title 都依赖它），可选 `meta.subtitle`（顶栏副标题）。
- 命名约定：path 用 kebab-case（如 content-review），name 与之一致。

## 8. API 层规范

> 接口统一规则见根目录 [`docs/api-contract.md`](../../docs/api-contract.md)，本文件只约定前端侧实现。

- 所有接口统一走 `src/api/http.ts` 导出的 request<T>(config)，它返回 Promise<T>（已解包 Result，业务代码直接拿 data），并统一处理 code !== 0 与网络错误的 ElMessage 提示。
- 不要绕过 request 裸调 axios；http 默认导出仅供拦截器/特殊场景使用。
- 后端统一响应结构见 `src/api/types.ts`：Result<T>（code=0 成功）、PageResult<T>（records/total/page/size）。
- 新增接口时在 `src/api/` 下按域建文件（如 `src/api/user.ts`），导出 async 函数，返回类型显式标注：

  ```ts
  import { request } from './http'
  import type { PageResult } from './types'

  export interface AdminUser { id: string; nickname: string }

  export function listUsers(params: { keyword?: string; page: number; size: number }) {
    return request<PageResult<AdminUser>>({ url: '/users', method: 'get', params })
  }
  ```

- 鉴权待接入：登录后需在 http.ts 的请求拦截器附加 Authorization 请求头，并在响应拦截器处理 401（清理登录态 + 跳登录页）。
- 敏感数据（手机号、情绪数据）默认脱敏展示，不得打印到控制台。

## 9. 状态管理（Pinia）

- 目前仅 createPinia() 挂载，尚无 store。
- 约定：按域建 `src/stores/`，采用 setup store 风格；仅跨组件共享的状态才进 store，页面内局部状态留在组件内。

## 10. 样式规范

- 设计变量集中在 `src/styles/index.css` 的 :root：颜色（--bg/--surface/--text/--primary 等）、圆角 --radius、阴影 --shadow、字体 --font。写样式时用变量，不写死色值。
- Element Plus 主题已通过 --el-color-primary、--el-border-radius-base、--el-font-family 等变量覆盖，调整主题只改 :root。
- 类名用 kebab-case，语义化命名，复用已有通用类：.page（页面容器）、.panel/.panel-head/.panel-foot、.notice、.badge、.stat-card、.module-card。
- 避免内联样式（style 属性）；覆盖 Element Plus 样式时用其 --el-* 变量或带作用域的类选择器。
- 响应式断点已定义在 index.css：1100px / 860px / 560px，新增布局需考虑这三个档位。

## 11. 环境变量与构建

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| VITE_API_BASE_URL | /admin/api | 浏览器侧接口前缀（开发/生产一致） |
| VITE_API_PROXY_TARGET | http://127.0.0.1:8080 | 仅开发环境生效的代理目标（网关） |

- 开发环境读取 .env.development，生产读取 .env.production；本地覆盖可建 .env.local（已 gitignore）。
- 生产由 Nginx 托管 dist/ 静态资源并反代 /admin/api 到网关（配置示例见 microhoov-admin-web/README.md）。
- 新增 VITE_* 变量：写入对应 env 文件，并在 vite-env.d.ts 的 ImportMetaEnv 中声明。

## 12. 安全与隐私约定

- 管理端使用独立鉴权链（独立 Token 密钥 + MFA），不复用 App 端鉴权；当前骨架顶部有「认证待接入」徽标占位。
- 敏感字段（手机号、情绪/量表数据）默认脱敏展示，查看明文需专项授权并记录审计。
- 匿名内容反查需专项授权并留痕。
- index.html 已设 `<meta name="robots" content="noindex, nofollow">`，勿移除。

## 13. 骨架阶段约定（「待接入」占位）

当前页面为骨架，数据以 `--` 或 `<span class="badge">待接入</span>` 占位。约定：

- 接口未接前用本地数组/常量占位（如 DashboardView.vue 的 stats）。
- 接入 admin-service 后，将占位数据替换为 request<T>() 调用，并移除「待接入」徽标。
- 表格空数据用 empty-text 说明接入状态（参见 UsersView.vue）。

## 14. 下一步

1. 接入管理端登录、RBAC 与路由守卫（router.beforeEach）。
2. 对接 admin-service 的仪表盘、用户、审核、运营、系统接口。
3. 按页面引入图表库、表格分页、操作审计组件。
4. （可选）引入 ESLint + Prettier，把第 5 节的风格约定固化到工具配置。
