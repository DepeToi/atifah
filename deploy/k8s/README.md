# Kubernetes 部署说明（占位）

后续在此目录补充 Helm Chart / Kustomize 清单，建议最小包含：

- `namespace.yaml`：多环境命名空间
- `deployment.yaml`：每个服务 Deployment + readiness/liveness/startup 探针
- `service.yaml`：ClusterIP Service
- `ingress.yaml`：Ingress-Nginx 路由与金丝雀发布
- `hpa.yaml`：基于 CPU 与 QPS（Prometheus Adapter）的自动扩缩容
- `pdb.yaml`：PodDisruptionBudget
- `configmap.yaml` / `secret.yaml`：非敏感配置与密钥（生产建议结合 Nacos + KMS）
- NetworkPolicy、ServiceMonitor、ArgoCD Application

生产建议：MySQL、Redis、RocketMQ、Nacos 优先使用云托管中间件；自建时使用 Operator/StatefulSet + PVC。
