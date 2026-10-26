# Nacos 配置

本目录存放配置中心（Nacos）的**源文件**，便于版本管理。运行前需将其发布到 Nacos。

## 命名空间

按环境隔离，ID 与显示名一致；客户端只认 **ID**，由环境变量 `NACOS_NAMESPACE` 注入
（各服务为 `namespace: ${NACOS_NAMESPACE:atifah_dev}`）：

| namespace | 环境 | 注入方式 |
|---|---|---|
| `atifah_dev` | 本地开发 | 默认值，无需设置 |
| `atifah_test` | 测试 | `NACOS_NAMESPACE=atifah_test` |
| `atifah_prod` | 生产 | `NACOS_NAMESPACE=atifah_prod`（`deploy/k8s/configmap.yaml` 已设） |

默认命名空间 `public` 不放业务配置，保持为空。发布配置时 v3 API 带 `namespaceId` 参数，
控制台先在顶部切换到目标命名空间。

## Group 划分

每个 namespace 内的配置按公共/私有拆分 group：

- 所有配置（公共与私有）的**归属应用**统一为 `atifah`。
- 公共配置统一放 **`ATIFAH_PUBLIC_GROUP`**（当前仅 `common-application.yaml`）。
- 各微服务私有配置放**各自的 group**：`ATIFAH_<服务名大写>_GROUP`，如 `ATIFAH_AUTH_GROUP`、`ATIFAH_MOOD_GROUP`、`ATIFAH_GATEWAY_GROUP`、`ATIFAH_ADMIN_GROUP`。
- 服务注册发现的 group 与配置 group 无关，统一保持 `atifah_GROUP`；所有服务必须同组，网关 `lb://` 路由才能发现实例，不要改动。

## 客户端鉴权

各服务从 `spring.cloud.nacos.{discovery,config}.username/password` 读取客户端凭据，值来自环境变量
`NACOS_USERNAME` / `NACOS_PASSWORD`（本地默认空）。本地 compose 已关闭鉴权，留空即可；
开启鉴权的环境（生产）必须注入，缺失时服务注册与配置拉取会被 403 拒绝。生产建议创建专用
应用账号（最小权限，不复用 `nacos` 管理员），由 `deploy/k8s/secret.yaml`（生产为
SealedSecret / External Secrets）经 `envFrom.secretRef` 注入。

## 导入顺序与优先级

各服务 `spring.config.import` 先导入公共配置、再导入私有配置：同一 `import` 列表中**后导入的优先级更高**，
因此私有配置可覆盖公共配置的同名项；本地 `application.yml` 自身优先于所有导入项，用于本地差异化兜底。

其中 `common-application.yaml` 为**必选导入**（不带 `optional:`）：Nacos 不可达或未发布该配置时服务
直接启动失败；各服务私有配置仍为可选（`optional:`），未发布时以本地默认值兜底。

## 配置清单

| data-id | Group | 用途 |
|---|---|---|
| `common-application.yaml` | `ATIFAH_PUBLIC_GROUP` | 10 个业务服务共享配置（Redis/MyBatis-Plus/Jackson/Sentinel/OpenFeign/RocketMQ/Actuator/JWT/日志） |
| `${spring.application.name}.yaml` | 各服务自己的 `ATIFAH_<服务名大写>_GROUP` | 各服务私有覆盖配置（可选，用于按服务覆盖共享项） |

## 发布方式

> Nacos 3.x 已移除 v1/v2 Admin API，下面的示例统一使用 v3 接口；v1 示例在 3.x 会直接 404。
> 示例以 `atifah_dev` 为例，发其他环境替换 `namespaceId` 即可。

**方式一：Nacos 控制台**（本地 <http://localhost:8849/>，控制台在根路径，不是 `/nacos`）

本地 compose 通过 `NACOS_AUTH_ADMIN_ENABLE=false` 关闭了运维 API 鉴权，可直接操作；
生产环境该开关必须开启，需先用管理员账号 `nacos` 登录。

控制台顶部命名空间切到 `atifah_dev`，依次新建配置：
- Data ID：`common-application.yaml`
- Group：`ATIFAH_PUBLIC_GROUP`
- 归属应用：`atifah`
- 格式：YAML
- 内容：`config/common-application.yaml` 全文

各服务私有配置按需发布：Data ID `${spring.application.name}.yaml`，Group 用该服务自己的组，归属应用同为 `atifah`。

**方式二：OpenAPI 脚本**（需先启动 Nacos，并在 `deploy/docker-compose/.env` 配好 `NACOS_AUTH_TOKEN`）

```bash
curl -X POST "http://127.0.0.1:8848/nacos/v3/admin/cs/config" \
  --data-urlencode "namespaceId=atifah_dev" \
  --data-urlencode "dataId=common-application.yaml" \
  --data-urlencode "groupName=ATIFAH_PUBLIC_GROUP" \
  --data-urlencode "appName=atifah" \
  --data-urlencode "type=yaml" \
  --data-urlencode "content@config/common-application.yaml"
```

注意 v3 参数名是 `groupName`（v1 为 `group`）。鉴权开启时请求还需带 `accessToken`，
本地 compose 已关闭运维 API 鉴权，可直接调用。

## 本地开发

```bash
docker compose -f deploy/docker-compose/docker-compose.yml up -d nacos
```

发布 `common-application.yaml`（命名空间 `atifah_dev`）后再启动各服务。各服务通过
`spring.config.import: nacos:common-application.yaml?group=ATIFAH_PUBLIC_GROUP` **必选**引入，
未发布时服务直接启动失败，故本地开发务必先发布。
