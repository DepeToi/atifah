# Windows 11 本地开发部署说明

本文档说明在 Windows 11 上从零搭建并运行 atiFah 开发环境。
后端（网关、9 个业务服务、`admin-service`）提供两套运行方案，中间件、Nacos 配置与构建产物两边共用：

| 方案 | 中间件 | 后端运行方式 | 适用场景 |
| --- | --- | --- | --- |
| A. JVM（推荐日常开发） | Docker Compose | 宿主机 `java -jar` / IDEA | 断点调试、改代码即重启 |
| B. Docker | Docker Compose | 后端容器（`docker-compose.app.yml`） | 镜像验证、环境一致性联调 |

> 两套方案端口完全一致，**同一时间只能用一套**；切换前先停掉另一套的后端进程/容器。
> 架构总览见 `docs/architecture.md`，中间件端口与快速开始见根 `README.md`。

## 1. 前置条件

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21+ | `JAVA_HOME` 指向 JDK 21，`java -version` 输出 21.x |
| Maven | 3.9+ | 两套方案都用它在宿主机构建 jar |
| Node.js | `^20.19.0 \|\| >=22.12.0` | 仅前端需要（Vite 8 要求） |
| Docker Desktop | 最新 | 中间件依赖，启用 WSL2 后端；方案 B 还需要它构建/运行后端镜像 |
| WSL2 | 已安装 | Docker Desktop 后端；无则先 `wsl --install` |

> 仅 `mvn compile` 不需要 Docker；启动服务前必须先把中间件跑起来。
> 方案 B 的镜像只打包宿主机已构建的 jar，不在容器内执行 Maven，因此仍需要 JDK + Maven。

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

> 中间件 compose 固定网络名 `atifah-net`，方案 B 的后端容器以 `external` 方式接入该网络；
> 不要删除或重命名这个网络。

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

### 3.3 Nginx（可选，本地静态托管验证）

Nginx 托管管理后台前端构建产物，并把 `/admin/api` 反代到宿主机网关；日常开发
前端走 `npm run dev`（Vite 代理），Nginx 非必需。需先构建前端产物再启动：

在仓库根目录执行：

```bash
(cd atifah-admin-web/frontend && npm run build)
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

## 5. 构建后端产物

两套方案共用同一份 jar，先执行：

```bash
mvn clean package -DskipTests
```

快速编译自检用 `mvn -o -q -DskipTests compile`。

## 6. 启动后端

启动顺序：先网关，再按需启动业务服务。两套方案任选其一。

### 6.1 方案 A：JVM 运行

IDEA 直接运行 `*ServiceApplication`，或：

```bash
java -jar atifah-gateway/target/atifah-gateway-1.0.0-SNAPSHOT.jar
java -jar atifah-services/user-service/target/user-service-1.0.0-SNAPSHOT.jar
java -jar atifah-admin-web/backend/target/admin-service-1.0.0-SNAPSHOT.jar
```

服务经 Nacos 注册，网关按 `lb://atifah-<service>` 路由。默认连接中间件地址为
`127.0.0.1`（环境变量可覆盖，见根 README「配置约定」）。

### 6.2 方案 B：Docker 运行

前置条件：中间件已启动（§3）、Nacos 配置已导入（§4）、jar 已构建（§5）。

> 若中间件是在网络名固定为 `atifah-net` 之前启动的，请先重新执行一次 §3 的 `up -d`
> （容器会重建，`mysql-data` 等数据卷保留），否则方案 B 会提示找不到网络 `atifah-net`；
> 旧网络 `atifah_atifah-net` 若残留可执行 `docker network rm atifah_atifah-net` 清理。

构建镜像并启动全部后端：

```bash
docker compose -f deploy/docker-compose/docker-compose.app.yml up -d --build
```

首次构建会拉取基础镜像并逐个打镜像；日常只启动需要的服务即可（端口与方案 A 相同，不能同时运行）：

```bash
docker compose -f deploy/docker-compose/docker-compose.app.yml up -d --build gateway user-service
```

单个服务也可以脱离统一编排独立构建/启停。每个后端模块目录（`atifah-gateway/`、
`atifah-services/*/`、`atifah-admin-web/backend/`）下都有 `Dockerfile`、`.dockerignore` 与
`docker-compose.yml`，构建上下文就是该模块目录：

```bash
docker compose -f atifah-services/user-service/docker-compose.yml up -d --build
docker compose -f atifah-services/user-service/docker-compose.yml logs -f
docker compose -f atifah-services/user-service/docker-compose.yml down
```

单服务 compose 与统一 compose 的镜像 tag、容器名、端口完全一致，**同一个服务不能同时用两种方式运行**；
统一 compose 只是复用同一批模块 Dockerfile 做全量编排。

说明：

- 镜像由各服务模块目录下的 `Dockerfile` 构建，构建上下文是模块目录，只打包该模块的 `target/*.jar`；
  改代码后先重新 `mvn package`，再 `up -d --build <服务名>`。
- 网关与 `auth-service` 的 `docker-compose.yml` 以注释预留 `JWT_SECRET`；非开发环境必须为两者注入相同密钥。
- 容器内通过 compose 服务名访问中间件（`mysql`、`redis`、`nacos`、`rocketmq-namesrv`），
  地址由 `docker-compose.app.yml` 注入，**不要**在容器内使用 `127.0.0.1`。
- 单个容器默认内存上限 1g（`APP_MEM_LIMIT` 可改）；全部 11 个后端容器的内存上限合计约 11GB，
  建议按需启动。
- 国内网络拉取基础镜像受限时，可覆盖镜像源后再构建：
  ```bash
  BASE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21-jre-jammy \
    docker compose -f deploy/docker-compose/docker-compose.app.yml up -d --build
  ```
- 单服务 compose 不读取 `deploy/docker-compose/.env`；覆盖 `BASE_IMAGE` / `APP_MEM_LIMIT` 等变量时用
  shell 环境变量，或在对应模块目录放 `.env`（已被 `.gitignore` 忽略）。

查看状态与日志：

```bash
docker compose -f deploy/docker-compose/docker-compose.app.yml ps
docker compose -f deploy/docker-compose/docker-compose.app.yml logs -f gateway
```

停止/清理（只影响后端容器，不动中间件）：

```bash
docker compose -f deploy/docker-compose/docker-compose.app.yml down
```

容器与镜像的生命周期、删除镜像与回收磁盘见 §6.3。

### 6.3 容器与镜像生命周期

| 操作 | 命令 | 容器 | 镜像 |
| --- | --- | --- | --- |
| 停止服务，保留容器 | `docker compose -f deploy/docker-compose/docker-compose.app.yml stop` | 停止 | 保留 |
| 删除容器，保留镜像 | `docker compose -f deploy/docker-compose/docker-compose.app.yml down` | 删除 | 保留 |
| 删除容器与服务镜像 | `docker compose -f deploy/docker-compose/docker-compose.app.yml down --rmi all` | 删除 | 删除 |
| 清理悬空镜像 | `docker image prune` | 不变 | 删除 `<none>` |

- 镜像不会随容器停止自动删除：容器只是镜像的运行实例；`stop` / `down` 都不影响镜像。
- 本 compose 为每个服务设置了 `image: atifah/<service>:local`（自定义标签），所以 `down --rmi local`
  **不会**删除这些镜像，必须用 `--rmi all` 或按名 `docker image rm`。
- 改代码后重新 `mvn package` + `up -d --build <服务>` 会用同名 tag 覆盖旧镜像，旧镜像变成悬空镜像
  （`<none>`），可用 `docker image prune` 回收；构建缓存可用 `docker builder prune` 清理。
- 单个后端镜像约 540–710MB（含 JRE，基础层共享），11 个逻辑大小合计约 7GB；
  镜像保留时下次 `up -d` 无需重新构建，只启动需要的服务即可。
- 不要用 `docker system prune -a`：它会连 Nacos / MySQL / Redis 等中间件镜像一起删除，导致下次
  启动需要重新拉取。

## 7. 启动管理后台前端

```bash
cd atifah-admin-web/frontend
npm install
npm run dev
```

访问 <http://localhost:5173>（Vite 代理 `/admin/api` 到网关）。

## 8. 健康检查与排障

两套方案端口一致：

```bash
curl http://localhost:8080/actuator/health   # Gateway
curl http://localhost:8082/actuator/health   # user-service
curl http://localhost:8090/actuator/health   # admin-service
```

方案 B 额外检查：

```bash
docker compose -f deploy/docker-compose/docker-compose.app.yml ps
```

- 容器启动后首次健康检查通过即转为 `healthy`（宽限期 60s 内失败不计入重试）；期间 `curl` 返回空属正常。
- 业务服务启动失败时先确认 Nacos 配置已发布到 `atifah_dev`，再查看 `logs <服务名>`。
- 若出现 `ERR AUTH <password> called without any password configured`，是 `REDIS_PASSWORD` 为空串
  触发 Redisson 发送 AUTH；`common-redis` 已统一把空白密码归一化为 null，重新构建 jar/镜像即可。
