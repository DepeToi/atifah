# Windows 11 本地开发部署说明

本文档说明在 Windows 11 上从零搭建并运行 atiFah 开发环境。
生产部署见 `docs/architecture.md` §8 与 `k8s/`；中间件端口与快速开始见根 `README.md`。

## 1. 前置条件

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21+ | `JAVA_HOME` 指向 JDK 21，`java -version` 输出 21.x |
| Maven | 3.9+ | |
| Node.js | `^20.19.0 \|\| >=22.12.0` | 仅前端需要（Vite 8 要求） |
| Docker Desktop | 最新 | 中间件依赖，启用 WSL2 后端 |
| WSL2 | 已安装 | Docker Desktop 后端；无则先 `wsl --install` |

> 仅 `mvn compile` 不需要 Docker；启动服务前必须先把中间件跑起来。

## 2. 启用 WSL2 + Docker Desktop

Docker Desktop 使用 WSL2 后端（Windows 11 默认）：

1. 管理员 PowerShell：`wsl --install`（装好 Ubuntu 发行版并重启）。
2. 安装 Docker Desktop，Settings → General → 勾选 `Use the WSL 2 based engine`。
3. 验证：`docker compose version`、`docker info`。
4. 内存不足时给 WSL2 限内存：在 `%UserProfile%\.wslconfig` 写
   ```
   [wsl2]
   memory=8GB
   processors=4
   ```

## 3. 启动中间件

先准备本地环境变量：Nacos 3 启动脚本强制要求 `NACOS_AUTH_TOKEN`（base64，解码后 ≥32 字节），
缺失时 compose 会直接报错退出。

```bash
cd deploy/docker-compose
cp .env.example .env
openssl rand -base64 32        # 把输出填入 .env 的 NACOS_AUTH_TOKEN
```

`.env` 已被 `.gitignore` 忽略，禁止提交；`.env.example` 只放生成方式。

然后一键拉起全部中间件：

```bash
docker compose -f deploy/docker-compose/docker-compose.yml up -d
```

会拉起 MySQL / Redis / Nacos / RocketMQ / Sentinel（Seata `--profile seata`、
Nginx `--profile nginx` 按需启用）。
常用地址（完整表见根 README）：

| 组件 | 地址 |
| --- | --- |
| MySQL | `127.0.0.1:3306`（root/root） |
| Redis | `127.0.0.1:6379`（无密码） |
| Nacos 注册/配置 | `127.0.0.1:8848` |
| Nacos 控制台 | <http://localhost:8849> |
| RocketMQ Dashboard | <http://localhost:8180> |
| Sentinel Dashboard | <http://localhost:8858>（sentinel/sentinel） |
| Nginx（可选） | <http://localhost> |

### 3.1 MySQL

```bash
docker compose -f deploy/docker-compose/docker-compose.yml up -d mysql
```

首次启动自动执行 `deploy/sql/01-init-databases.sql`，创建 `atifah_*` 共 10 个库。验证：

```bash
docker exec -it atifah-mysql mysql -uroot -proot -e "SHOW DATABASES;"
```

### 3.2 Redis

```bash
docker compose -f deploy/docker-compose/docker-compose.yml up -d redis
```

验证（返回 `PONG`）：

```bash
docker exec -it atifah-redis redis-cli ping
```

### 3.3 Nginx（可选，模拟生产静态托管）

Nginx 托管管理后台前端构建产物，并把 `/admin/api` 反代到宿主机网关；纯开发阶段
前端走 `npm run dev`（Vite 代理），Nginx 非必需。需先构建前端产物再启动：

```bash
cd atifah-admin-web/frontend && npm run build
docker compose -f deploy/docker-compose/docker-compose.yml up -d nginx
```

配置见 `deploy/nginx/nginx.conf`。访问 <http://localhost> 即看到构建后的管理后台；
若 80 端口被占用，改 `docker-compose.yml` 中 nginx 的宿主端口。

## 4. 导入 Nacos 配置

先把共享配置 `deploy/nacos/config/common-application.yaml` 发布到 Nacos
（命名空间 `atifah_dev`，Data ID `common-application.yaml`，Group `ATIFAH_PUBLIC_GROUP`，格式 YAML）。
控制台 <http://localhost:8849/> 导入，或用 v3 运维 API 脚本导入（见 `deploy/nacos/README.md`）；
未发布、不可达或装载标记缺失时，业务服务会拒绝启动；请先确认配置已发布到正确的 namespace 和 group。

Nacos 配置已持久化到 MySQL（库 `nacos_config`，表结构见 `deploy/sql/02-nacos-config.sql`），
容器重建后配置不丢失；仅在 mysql-data 卷清空时才需重新导入。

## 5. 编译

```bash
mvn clean package -DskipTests
```

快速编译自检用 `mvn -o -q -DskipTests compile`。

## 6. 启动后端

顺序：先网关，再按需启动业务服务。IDEA 直接运行 `*ServiceApplication`，或：

```bash
java -jar atifah-gateway/target/atifah-gateway-1.0.0-SNAPSHOT.jar
java -jar atifah-services/user-service/target/user-service-1.0.0-SNAPSHOT.jar
```

服务经 Nacos 注册，网关按 `lb://atifah-<service>` 路由。默认连接中间件地址为
`127.0.0.1`（环境变量可覆盖，见根 README「配置约定」）。

## 7. 启动管理后台前端

```bash
cd atifah-admin-web/frontend
npm install
npm run dev
```

访问 <http://localhost:5173>（Vite 代理 `/admin/api` 到网关）。

## 8. 健康检查

```bash
curl http://localhost:8080/actuator/health   # Gateway
curl http://localhost:8082/actuator/health   # user-service
curl http://localhost:8090/actuator/health   # admin-service
```

## 9. 可选：K8s 方式

生产/集群化用 `deploy/k8s/` 清单（最小可跑版，说明见 `deploy/k8s/README.md`）：

1. Docker Desktop Settings → Kubernetes → 启用内置 K8s。
2. 安装 Ingress-Nginx 控制器。
3. 中间件仍由宿主机 docker-compose 提供，`configmap.yaml` 已默认用 `host.docker.internal` 指向宿主机。
4. 补齐镜像构建（仓库暂未配 Jib/Dockerfile）后：
   ```bash
   kubectl apply -f deploy/k8s/
   ```

> 本地纯开发阶段用 Docker Compose + 本地进程即可，K8s 属于生产化（P4）路径。
