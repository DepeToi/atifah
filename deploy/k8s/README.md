# Kubernetes 部署说明

本目录提供**最小可跑**的 K8s 清单，用于把无状态应用层（网关 + 业务服务）跑在集群里。
中间件（MySQL/Redis/RocketMQ/Nacos）不在本目录内——生产用云托管，本地用 docker-compose。

## 清单

| 文件 | 作用 |
| --- | --- |
| `namespace.yaml` | `atifah-prod` 命名空间 |
| `configmap.yaml` | 非敏感中间件连接配置（`atifah-env`） |
| `secret.yaml` | 敏感配置（`atifah-secret`，生产必须替换） |
| `gateway.yaml` | 网关 Deployment + Service（8080） |
| `user-service.yaml` | 业务服务模板（8082，其余服务复制改端口） |
| `ingress.yaml` | Ingress-Nginx 路由 → gateway-service |

## 前置条件

1. 集群就绪（Docker Desktop 内置 K8s / minikube / kind），并已安装 Ingress-Nginx 控制器。
2. 中间件可达：本地用 docker-compose 起在宿主机，`configmap.yaml` 默认用 `host.docker.internal` 指向宿主机；生产改为云托管地址（Kustomize overlay 覆盖）。
3. 创建名为 `atifah-secret` 的 Secret，包含清单所需的数据库、Redis、JWT 密钥及生产 Nacos 客户端凭据 `NACOS_USERNAME` / `NACOS_PASSWORD`。生产部署不要直接应用仓库中的开发用 `secret.yaml`；应通过 External Secrets、SealedSecret 等安全机制提供同名 Secret，仓库不提供可直接使用的 Nacos 默认账号。
4. 构建并导入镜像。本仓库暂未配 Jib/Dockerfile，先在 CI 或本地补齐镜像构建，再 tag 成清单里的镜像名（`atifah/gateway-service:latest`、`atifah/user-service:latest`）。

## 部署

```bash
kubectl apply -f deploy/k8s/
kubectl -n atifah-prod get pods,svc,ingress
```

本地测试：把 `atifah.local` 指向集群入口（Docker Desktop 映射到 127.0.0.1），
然后 `curl http://atifah.local/actuator/health`。

## 注意

- 服务经 Nacos 注册，网关用 `lb://` 直连 Pod IP，业务服务的 K8s Service 仅用于监控/未来直连。
- `secret.yaml` 中的数据库和 JWT 值仅为本地开发默认值，生产必须替换（JWT_SECRET 至少 256 位，密码走 KMS/SealedSecret）；Nacos 客户端凭据需另行安全注入。
- 生产化再补：HPA、PDB、NetworkPolicy、ServiceMonitor、金丝雀 Ingress、ArgoCD Application。
- 中间件自建时使用 Operator/StatefulSet + PVC，不放进本目录的应用清单。
