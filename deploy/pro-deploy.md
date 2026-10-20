# 云服务生产部署说明

本文档说明在云服务上部署 atiFah 生产环境。架构总览、Nginx 部署与上线检查清单见
`docs/architecture.md` §8；最小 K8s 清单见 `k8s/`；本地开发见 `dev-deploy.md`。

## 1. 前置准备

| 项 | 说明 |
| --- | --- |
| K8s 集群 | 云托管（ACK/TKE/EKS）或自建，>=3 节点，跨可用区 |
| Ingress-Nginx | 集群入口，前置云 LB（SLB/CLB） |
| 域名与证书 | 生产域名 + cert-manager 自动签发/续期 TLS 证书 |
| 镜像仓库 | Harbor 或云容器镜像服务（CI 构建产物统一推到这里） |
| 密钥管理 | KMS / Vault（敏感字段加密密钥、JWT 密钥、DB 密码） |

## 2. 中间件（优先云托管）

| 组件 | 生产方案 |
| --- | --- |
| MySQL | 云 RDS 主从（或一服务一实例，初期可共实例独立 Schema） |
| Redis | 云 Redis（Cluster 3 主 3 从） |
| RocketMQ | 云 RocketMQ 或自建 namesrv×2 + broker×2 |
| Nacos | 云 MSE-Nacos 或自建 StatefulSet×3 |
| 其他 | Sentinel Dashboard / XXL-Job / Seata 按需自建（见架构 §8） |

自建时用 Operator/StatefulSet + PVC，不进 `k8s/` 应用清单。

## 3. 配置准备

1. **Nacos**：把 `deploy/nacos/config/common-application.yaml` 与各服务私有配置发布到生产
   Nacos（group `atifah_GROUP`）；生产差异化项用 Nacos 或环境变量覆盖。
   Nacos 3 的鉴权相关环境变量必须显式配置，值由 KMS / External Secrets 注入，禁止落仓库：
   - `NACOS_AUTH_TOKEN`：base64，解码后 ≥32 字节，缺失时启动脚本直接 exit 255
   - `NACOS_AUTH_IDENTITY_KEY` / `NACOS_AUTH_IDENTITY_VALUE`：服务端身份，缺失同样启动失败
   - `NACOS_AUTH_ENABLE=true`，并保持 `nacos.core.auth.admin.enabled=true`（运维 API 鉴权默认开启，
     生产不得像本地 compose 那样用 `NACOS_AUTH_ADMIN_ENABLE=false` 关闭）
2. **Secret 替换**：`k8s/secret.yaml` 内是开发默认值，生产必须替换——
   `JWT_SECRET` 至少 256 位强随机（或切 RS256）、`MYSQL_PASSWORD` / `REDIS_PASSWORD` 走 KMS；
   推荐 SealedSecret / External Secrets 注入，不落明文到仓库。
3. **ConfigMap 覆盖**：`k8s/configmap.yaml` 默认 `host.docker.internal`，生产改为云托管地址；
   `CORS_ALLOWED_ORIGINS` 收敛为具体域名。用 Kustomize overlay 覆盖，不直接改源清单。
4. **敏感字段加密**：日记正文/量表结果/手机号字段级加密（AES-256-GCM），密钥由 KMS 管理。
5. **Ingress**：`k8s/ingress.yaml` 的 `atifah.local` 换成生产域名，配置 TLS 与安全响应头。

## 4. 镜像构建与推送

仓库暂未配 Jib/Dockerfile，需先在 CI 补齐镜像构建：

```
Git Push → 单测 + SonarQube → Maven 构建 → Jib 镜像 → Harbor
       → 镜像扫描（Trivy）→ 推生产仓库
```

把 `k8s/` 清单里的 `atifah/<service>:latest` 换成仓库里的 `<registry>/atifah/<service>:<tag>`
（用不可变 tag，如 commit SHA，不用 latest）。

## 5. 部署

```bash
# 先按 §3 覆盖生产配置，再应用
kubectl apply -f deploy/k8s/
kubectl -n atifah-prod get pods,svc,ingress
```

生产建议改用 ArgoCD GitOps 同步（见架构 §8 CI/CD），避免手工 apply。

## 6. 数据库迁移

- Flyway 管理 DDL；`deploy/sql/` 只含初始化 Schema，生产迁移脚本随服务发布、按顺序执行。
- 迁移在发布前跑，失败回滚（详见 `docs/rules/database.md`）。

## 7. 网络与安全

- 唯一入口是网关（经 Ingress-Nginx）；禁止绕过网关直连业务服务。
- 管理端 `/admin/api` 加 IP 白名单 + MFA；admin-web 静态 Nginx 独立白名单。
- TLS1.3/HTTP2 在入口终止，HSTS + 安全响应头；WAF 前置。
- NetworkPolicy 限制服务间访问；容器非 root、镜像 Trivy 扫描。

## 8. 发布与回滚

- 滚动更新 + 金丝雀（Argo Rollouts 或 Nginx Ingress 权重）5% → 25% → 100%，Prometheus
  指标异常自动回滚。
- HPA（CPU 70% + QPS 指标）+ PDB + 跨可用区反亲和保证滚动更新可用性。

## 9. 上线检查清单

发布前逐项核对 `docs/architecture.md` §8「部署检查清单」：中间件就绪、配置/密钥、
Flyway、安全（TLS/WAF/白名单）、网关、容量、可观测、网络、发布与回滚、验证。

## 10. 可观测性

接入 Prometheus / Grafana / Loki / SkyWalking，告警覆盖：错误率、P99 超阈值、MQ 堆积、
消费失败、DB 主从延迟、证书到期（目标值见架构 §9）。
