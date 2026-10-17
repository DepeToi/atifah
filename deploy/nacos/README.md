# Nacos 配置

本目录存放配置中心（Nacos）的**源文件**，便于版本管理。运行前需将其发布到 Nacos。

## 配置清单

| data-id | 用途 |
|---|---|
| `common-application.yaml` | 10 个业务服务共享配置（Redis/MyBatis-Plus/Jackson/Sentinel/OpenFeign/RocketMQ/Actuator/JWT/日志） |
| `${spring.application.name}.yaml` | 各服务私有覆盖配置（可选，用于按服务覆盖共享项） |

均使用 group `MICROHOOV_GROUP`。

## 发布方式

**方式一：Nacos 控制台**（本地 http://127.0.0.1:8849/nacos，默认无鉴权）

依次新建配置：
- Data ID：`common-application.yaml`
- Group：`MICROHOOV_GROUP`
- 格式：YAML
- 内容：`config/common-application.yaml` 全文

**方式二：OpenAPI 脚本**（需先启动 Nacos）

```bash
curl -X POST "http://127.0.0.1:8848/nacos/v1/cs/configs" \
  -d "dataId=common-application.yaml" \
  -d "group=MICROHOOV_GROUP" \
  -d "type=yaml" \
  --data-urlencode "content@config/common-application.yaml"
```

## 本地开发

```bash
docker compose -f deploy/docker-compose/docker-compose.yml up -d nacos
```

发布 `common-application.yaml` 后再启动各服务。各服务通过
`spring.config.import: optional:nacos:common-application.yaml` 引入，未发布时以
本地默认值兜底（MyBatis-Plus 逻辑删除等能力不生效，故本地开发务必先发布）。
