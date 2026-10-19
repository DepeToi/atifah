# Nacos 配置

本目录存放配置中心（Nacos）的**源文件**，便于版本管理。运行前需将其发布到 Nacos。

## 配置清单

| data-id | 用途 |
|---|---|
| `common-application.yaml` | 10 个业务服务共享配置（Redis/MyBatis-Plus/Jackson/Sentinel/OpenFeign/RocketMQ/Actuator/JWT/日志） |
| `${spring.application.name}.yaml` | 各服务私有覆盖配置（可选，用于按服务覆盖共享项） |

均使用 group `atifah_GROUP`。

## 发布方式

> Nacos 3.x 已移除 v1/v2 Admin API，下面的示例统一使用 v3 接口；v1 示例在 3.x 会直接 404。

**方式一：Nacos 控制台**（本地 <http://localhost:8849/>，控制台在根路径，不是 `/nacos`）

本地 compose 通过 `NACOS_AUTH_ADMIN_ENABLE=false` 关闭了运维 API 鉴权，可直接操作；
生产环境该开关必须开启，需先用管理员账号 `nacos` 登录。

依次新建配置：
- Data ID：`common-application.yaml`
- Group：`atifah_GROUP`
- 格式：YAML
- 内容：`config/common-application.yaml` 全文

**方式二：OpenAPI 脚本**（需先启动 Nacos，并在 `deploy/docker-compose/.env` 配好 `NACOS_AUTH_TOKEN`）

```bash
curl -X POST "http://127.0.0.1:8848/nacos/v3/admin/cs/config" \
  --data-urlencode "dataId=common-application.yaml" \
  --data-urlencode "groupName=atifah_GROUP" \
  --data-urlencode "type=yaml" \
  --data-urlencode "content@config/common-application.yaml"
```

注意 v3 参数名是 `groupName`（v1 为 `group`）。鉴权开启时请求还需带 `accessToken`，
本地 compose 已关闭运维 API 鉴权，可直接调用。

## 本地开发

```bash
docker compose -f deploy/docker-compose/docker-compose.yml up -d nacos
```

发布 `common-application.yaml` 后再启动各服务。各服务通过
`spring.config.import: optional:nacos:common-application.yaml` 引入，未发布时以
本地默认值兜底（MyBatis-Plus 逻辑删除等能力不生效，故本地开发务必先发布）。
