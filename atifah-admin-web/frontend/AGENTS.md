# AGENTS.md — atifah-admin-web/frontend

> 模块级规则，与根 [`AGENTS.md`](../../AGENTS.md)、[`docs/rules/*`](../../docs/rules/)、上级 [`AGENTS.md`](../AGENTS.md) 叠加生效；冲突时以本文件为准（就近覆盖）。
> 人看的工程说明与部署方式见 [`../README.md`](../README.md)。

## 边界

- 前端只经网关 `/admin/api/**` 访问 `admin-service`，**禁止直连业务服务或数据库**。
- 管理端使用独立鉴权链（独立密钥 + MFA），不复用 C 端 Token；未接入前不要把登录逻辑写死在页面里。
- 敏感数据（手机号、情绪/量表）默认脱敏，禁止 `console` 输出。
- 接口契约以 `docs/contracts/api-conventions.md` 为准；`docs/contracts/openapi/admin-service.yaml` 由 Claude 在管理端接口冻结时补齐（当前待补），不得自行假设字段。

## 技术栈

Vue 3（Composition API + `<script setup>`）+ TypeScript（strict）+ Vite 8 + Element Plus 2.14 + Pinia + Vue Router + Axios。

## 工程速查

```text
frontend/
├── .env.development / .env.production
├── index.html                 # lang=zh-CN，robots noindex
├── vite.config.ts             # 5173 端口、/admin/api 代理、@ 别名
└── src/
    ├── main.ts                # Pinia / Router / Element Plus(zh-cn) / 全局样式
    ├── api/http.ts            # axios 实例 + request<T>() 解包
    ├── api/types.ts           # Result<T> / PageResult<T>
    ├── layouts/AdminLayout.vue
    ├── router/index.ts        # 路由表 + meta.title/subtitle
    ├── styles/index.css       # 设计变量 + 全局样式
    ├── views/                 # XxxView.vue
    └── vite-env.d.ts          # ImportMetaEnv 类型声明
```

```bash
npm install        # Node ^20.19.0 || >=22.12.0
npm run dev        # 开发服务器 :5173，代理 /admin/api -> 网关 :8080
npm run type-check # vue-tsc 类型检查
npm run build      # 类型检查 + 构建 -> dist/
npm run preview    # 本地预览构建产物
```

## TypeScript

- `strict`、`noUnusedLocals`、`noUnusedParameters`、`noFallthroughCasesInSwitch` 全开；未使用变量/参数会直接编译失败，不要留死代码。
- `verbatimModuleSyntax` 已开启：类型导入必须 `import type`，否则构建报错。
- 路径别名 `@/*` → `src/*`，同时配置在 `tsconfig.json` 与 `vite.config.ts` 两处，调整时两处都要改。
- 共享响应类型放 `src/api/types.ts`；业务模型按域拆分。
- 新增 `VITE_*` 变量时，同步在 `src/vite-env.d.ts` 的 `ImportMetaEnv` 中声明类型。

## 代码风格

现有代码一致遵循：无分号、单引号、2 空格缩进、尾逗号。仓库尚未引入 ESLint / Prettier，不要假设有自动格式化。

可读性遵循 `docs/rules/code-style.md` CODE-09；前端补充：优先提前返回，不为复用抽过多 composable，模板保持扁平，注释用中文。

```ts
import { ref } from 'vue'

const activeTab = ref('all')

const statusOptions = [
  { label: '正常', value: 'active' },
  { label: '已禁用', value: 'disabled' },
]
```

## 组件与路由

- 统一 `<script setup lang="ts">`，单文件组件顺序 script → template（需要时再加 style）。
- 视图组件命名 `XxxView.vue`，放 `src/views/`；布局放 `src/layouts/`。
- props/emits 用类型化的 `defineProps` / `defineEmits`；纯展示静态常量用普通数组/对象。
- 装饰性图标来自 `@element-plus/icons-vue`，用 `<el-icon>` 包裹；保持语义化标签与 aria 属性。
- 路由集中在 `src/router/index.ts`，页面组件全部懒加载；每个路由必须有 `meta.title`，可选 `meta.subtitle`；404 用 `/:pathMatch(.*)*` 兜底；path 用 kebab-case。

## API 层

- 所有接口统一走 `src/api/http.ts` 的 `request<T>()`，返回 `Promise<T>`（已解包 `Result`，业务代码直接拿 `data`）；不要绕过 `request` 裸调 axios。
- 后端统一响应见 `src/api/types.ts`：`Result<T>`（`code=0` 成功）、`PageResult<T>`（records/total/page/size）。
- 新增接口按域建文件（如 `src/api/user.ts`），导出 async 函数并显式标注返回类型：

```ts
import { request } from './http'
import type { PageResult } from './types'

export interface AdminUser { id: string; nickname: string }

export function listUsers(params: { keyword?: string; page: number; size: number }) {
  return request<PageResult<AdminUser>>({ url: '/users', method: 'get', params })
}
```

- 鉴权待接入：登录后需在 `http.ts` 请求拦截器附加 `Authorization`，响应拦截器处理 401（清理登录态 + 跳登录页）。
- 改接口结构时同步 `docs/contracts/api-conventions.md`、后端 `common-core` 与本目录 `types.ts` 三处。

## 状态与样式

- Pinia 目前仅 `createPinia()` 挂载，尚无 store。约定按域建 `src/stores/`，用 setup store 风格；仅跨组件共享状态才进 store。
- 设计变量集中在 `src/styles/index.css` 的 `:root`（颜色、圆角、阴影、字体）；写样式用变量，不写死色值。
- 类名 kebab-case，复用 `.page` / `.panel` / `.notice` / `.badge` / `.stat-card` 等通用类；避免内联 `style`。
- 覆盖 Element Plus 用 `--el-*` 变量或作用域类选择器；响应式断点 1100px / 860px / 560px。

## 环境变量与安全

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `/admin/api` | 浏览器侧接口前缀 |
| `VITE_API_PROXY_TARGET` | `http://127.0.0.1:8080` | 仅开发环境生效的代理目标（网关） |

- 本地覆盖建 `.env.local`（已 gitignore）；新增变量同步 `vite-env.d.ts`。
- `index.html` 已设 `<meta name="robots" content="noindex, nofollow">`，勿移除。
- 开发期由 Vite 代理 `/admin/api` 到网关；需要验证构建产物时用 `deploy/docker-compose` 的可选 nginx profile（见 `deploy/dev-deploy.md`）。

## 当前阶段

- 页面为骨架，数据用 `--` 或 `<span class="badge">待接入</span>` 占位；接入后替换占位并移除徽标。
- 表格空数据用 `empty-text` 说明接入状态。

## 自检

- 改动后执行 `npm run type-check`；提交前建议 `npm run build`。
- 不通过不得声称完成；禁止删除类型声明、关闭 strict、加 `@ts-ignore` 掩盖问题。
