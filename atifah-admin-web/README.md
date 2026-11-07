# atifah-admin-web

管理后台工程，前后端分离开发，统一放在本目录下：

| 目录          | 说明                                                   |
| ----------- | ---------------------------------------------------- |
| `frontend/` | Vue 3 + TypeScript + Vite + Element Plus 单页应用         |
| `backend/`  | Spring Boot 管理端 BFF（`admin-service`），端口 `8090`       |

请求链路：浏览器 → 网关 `/admin/api/**` → `admin-service`。

当前阶段仅搭建工程骨架与占位页面，未实现登录和业务接口。

## 目录结构

```text
atifah-admin-web/
├── frontend/
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts             # 开发代理：/admin/api -> 网关
│   ├── index.html
│   ├── public/
│   └── src/
│       ├── main.ts                # Pinia / Router / Element Plus
│       ├── App.vue
│       ├── api/                   # Axios 实例与统一响应类型
│       ├── layouts/               # 侧边栏 + 顶栏 + 内容区 + 页脚
│       ├── router/                # 路由表
│       ├── styles/                # 全局样式与主题变量
│       └── views/                 # 占位页面
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/atifah/admin/
│       └── resources/application.yml
└── README.md
```

`backend` 通过 `relativePath` 复用 `atifah-services` 的公共依赖与构建配置，但不再作为
`atifah-services` 的聚合子模块；根 `pom.xml` 直接聚合 `atifah-admin-web/backend`。

## 前端开发

前置条件：Node.js `^20.19.0 || >=22.12.0`；需要真实接口时，先启动网关（`8080`）与
`backend`（`8090`）。

```bash
cd atifah-admin-web/frontend
npm install
npm run dev
```

访问 <http://localhost:5173>。开发服务器会把 `/admin/api` 代理到
`VITE_API_PROXY_TARGET`（默认 `http://127.0.0.1:8080`）。

只查看页面骨架时，可以只启动 Vite；接口请求失败不影响页面渲染。

### 环境变量

| 变量                       | 默认值                     | 说明                   |
| ------------------------ | ----------------------- | -------------------- |
| `VITE_API_BASE_URL`      | `/admin/api`            | 浏览器侧接口前缀             |
| `VITE_API_PROXY_TARGET`  | `http://127.0.0.1:8080` | 开发代理目标（网关地址），仅开发环境生效 |

本地覆盖可创建 `frontend/.env.local`（已被 `.gitignore` 忽略）。

### 构建

```bash
cd atifah-admin-web/frontend
npm run type-check   # 仅类型检查
npm run build        # 类型检查 + 构建，产物在 dist/
npm run preview      # 本地预览构建产物
```

### 页面路由

| 路径                | 页面   |
| ----------------- | ---- |
| `/`               | 重定向到 `/dashboard` |
| `/dashboard`      | 仪表盘  |
| `/users`          | 用户管理 |
| `/content-review` | 内容审核 |
| `/operations`     | 运营配置 |
| `/system`         | 系统管理 |

## 后端运行

在仓库根目录执行：

```bash
mvn -pl atifah-admin-web/backend -am package -DskipTests
java -jar atifah-admin-web/backend/target/admin-service-1.0.0-SNAPSHOT.jar
```

未启动 Nacos 时可关闭注册与配置：

```bash
java -jar atifah-admin-web/backend/target/admin-service-1.0.0-SNAPSHOT.jar \
  --spring.cloud.nacos.discovery.enabled=false \
  --spring.cloud.nacos.config.enabled=false
```

- 端口：`8090`
- 服务名：`atifah-admin`
- 职责：管理后台 BFF、RBAC、审核工作台、运营配置；后续通过 OpenFeign / MQ 聚合各业务服务，不直连业务库。
- 已开启虚拟线程（`spring.threads.virtual.enabled=true`），请求处理使用虚拟线程。

## 本地静态托管（可选）

开发期前端使用 `npm run dev`（Vite 代理 `/admin/api`）。如需验证构建产物，可先构建，再用
Docker Compose 的可选 nginx profile 托管 `dist/` 并反代到宿主机网关：

在仓库根目录执行：

```bash
(cd atifah-admin-web/frontend && npm run build)
docker compose -f deploy/docker-compose/docker-compose.yml up -d nginx
```

配置见 `deploy/nginx/nginx.conf`，访问 <http://localhost>。该方式仅用于本地开发验证；
生产部署不在当前仓库范围。

## 接口约定

- 管理端接口统一由网关 `/admin/api/**` 转发到 `admin-service`，前端不直连业务服务或数据库。
- 网关当前不对 `/admin/api` 做前缀重写，`admin-service` 的接口路径直接以 `/admin/api` 开头。
- 响应结构统一为 `Result<T>`：`{ code, message, data, timestamp }`，`code = 0` 表示成功。
- 分页结构统一为 `PageResult<T>`：`{ records, total, page, size }`。
- 登录接入后，通过 `Authorization: Bearer <token>` 传递管理端令牌；401 统一清理登录态并跳转登录页。

## 后续迭代

1. 接入管理端登录、RBAC 与路由守卫。
2. 对接 `admin-service` 的仪表盘、用户、审核、运营与系统接口。
3. 按页面需要引入图表库、表格分页与操作审计组件。
