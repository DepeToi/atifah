# 情绪管理微服务项目：设计思路与技术架构

> 文档版本：v1.0（2026-10-18）
>
> 技术基线：JDK 21 / Spring Boot 3.5.x / Spring Cloud 2025.0.x / Spring Cloud Alibaba 2025.0.0.0

## 1. 设计目标与容量假设

**业务特点**

- **敏感数据**：情绪日记、量表结果属于敏感个人信息，隐私与加密优先。
- **明显写入高峰**：打卡集中在早 7–9 点、晚 21–23 点，需要削峰。
- **读多写少**：社区内容、冥想音频、文章以读为主，需要多级缓存。
- **社交与实时性**：树洞/社区涉及 Feed 流、点赞计数、实时消息。
- **合规要求高**：内容审核、危机干预、未成年人保护、数据可删除。

**容量假设**（可按比例缩容）

| 指标     | 假设       |
| ------ | -------- |
| 注册用户   | 1000 万   |
| DAU    | 100 万    |
| 峰值在线   | 20 万     |
| 打卡峰值   | 3000 QPS |
| 读峰值    | 3 万 QPS  |
| IM 长连接 | 5 万      |

**总体风格**：App 端前后端分离 + BFF；管理后台前端独立（Vue 3 SPA），后端由 admin-service 提供 BFF 接口；微服务 + 事件驱动 + 读写分离 + 多级缓存 + 最终一致。

## 2. 业务域与微服务拆分

业务域划分为账号、情绪、分析、社区、内容、消息、增长、运营八个域；对应 9 个 C 端业务服务 + `admin-service` BFF。完整的域职责、服务归属、数据边界与跨域规则见 [`domain.md`](domain.md)。

拆分原则：一个服务一个逻辑库（初期可共用一个 MySQL 实例，但 Schema 独立），禁止跨库 JOIN；跨服务同步查询走 OpenFeign，异步事件走 RocketMQ；初期允许合并部署（`auth` 并入 `user`、`growth` 并入 `user`），但代码边界与库边界保持不变。

## 3. 总体技术架构

```
┌───────────────────────────────────────────────────────────────────────┐
│ 客户端层   iOS/Android App（Flutter）   管理后台 Web（Vue 3 SPA）          │
└───────────────┬───────────────────────────────────┬───────────────────┘
                │ HTTPS                             │ HTTPS
┌───────────────▼───────────────────────────────────▼───────────────────┐
│ 接入层     CDN + 对象存储            Nginx                            │
│            TLS1.3、HTTP/2、静态缓存、WAF、IP/连接限流、灰度、安全响应头   │
└───────────────┬───────────────────────────────────┬───────────────────┘
                │                                   │
┌───────────────▼───────────────────────────────────▼───────────────────┐
│ 网关层     Spring Cloud Gateway（无状态多副本）                         │
│   /api/v1/**  C 端               /admin/api/**  管理端独立鉴权链         │
│   路由 / JWT 验签 / 限流 / 幂等键 / TraceId / 灰度 / 响应统一封装        │
└───────────────┬───────────────────────────────────────────────────────┘
                │ OpenFeign（同步查询） + RocketMQ（异步事件）+ WebSocket
┌───────────────▼───────────────────────────────────────────────────────┐
│ 业务服务层（Nacos 注册发现 + 配置中心）                                 │
│  auth   user   mood   analysis   community   content   message        │
│  growth   stats   admin(BFF)                                           │
│  公共能力：Sentinel 流控熔断、Seata 分布式事务、XXL-Job 定时任务         │
└───────┬──────────────┬──────────────┬──────────────┬──────────────────┘
        │              │              │              │
┌───────▼─────┐ ┌──────▼──────┐ ┌─────▼───────┐ ┌────▼────────┐
│ MySQL 8     │ │ Redis 7     │ │ RocketMQ 5  │ │ Nacos       │
│ 主从/分库分表│ │ 缓存/锁/计数 │ │ 异步/削峰/事务│ │ 注册/配置    │
└─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘
┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│ Sentinel    │ │ Seata       │ │ ES（可选）   │ │ OSS/MinIO   │
│ 流控/熔断    │ │ 分布式事务   │ │ 社区搜索      │ │ 语音/图片    │
└─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘
┌───────────────────────────────────────────────────────────────────────┐
│ 基础设施层  Docker + Docker Compose（开发环境）                       │
│ 可观测性：SkyWalking / Prometheus + Grafana / Loki / Alertmanager      │
└───────────────────────────────────────────────────────────────────────┘
```

### 技术选型

| 层次    | 选型                                                      | 理由                      |
| ----- | ------------------------------------------------------- | ----------------------- |
| 接入层   | Nginx                                                   | 静态资源托管、反向代理、限流/连接复用、WAF |
| 运行时   | JDK 21 + Spring Boot 3.5.x                              | LTS、虚拟线程、容器友好           |
| 微服务   | Spring Cloud 2025.0.x + Spring Cloud Alibaba 2025.0.0.0 | 版本通过 BOM 对齐             |
| 注册/配置 | Nacos                                                   | AP 注册中心，配置热更新、灰度开关      |
| 网关    | Spring Cloud Gateway                                    | 响应式、可编程限流，适合高并发入口       |
| 入口代理  | Nginx                                                   | TLS 终止、反向代理、静态资源与 IP 限流 |
| 管理后台  | Vue 3 + Vite + TypeScript + Element Plus                 | 前后端分离、组件生态成熟、独立构建与发布      |
| RPC   | OpenFeign（默认）/ Dubbo 3（热点链路可选）                          | 优先简单，避免双协议复杂度           |
| 流控熔断  | Sentinel                                                | 规则持久化到 Nacos，网关+服务双层防护  |
| 分布式事务 | Seata AT                                                | 仅用于少量强一致场景，避免滥用         |
| ORM   | MyBatis-Plus                                            | 分页插件；SQL 统一写 XML，逻辑清晰 |
| 分库分表  | ShardingSphere-JDBC                                     | 无侵入、支持分片+读写分离           |
| 数据库   | MySQL 8.0/8.4                                           | 事务、主从、成熟运维              |
| 缓存    | Redis 7 + Redisson + Caffeine                           | 分布式缓存/锁/计数 + 本地热点缓存     |
| 消息    | RocketMQ 5.x                                            | 高吞吐、事务消息、延时消息、顺序消息、死信队列 |
| 定时任务  | XXL-Job                                                 | 周报生成、对账补偿、批量任务          |
| 文件    | OSS/MinIO 预签名直传                                         | 服务端不中转大文件               |
| 可观测   | SkyWalking + Prometheus/Grafana + Loki                  | 链路、指标、日志三件套             |
| 容器    | Docker + Docker Compose                                 | 本地开发环境                   |
| CI    | GitHub Actions                                          | 构建、测试、安全扫描               |

> Servlet 业务服务统一启用 `spring.threads.virtual.enabled=true`，以虚拟线程处理 I/O 密集请求；Gateway 保持响应式事件循环，不启用该配置。自定义线程池、MQ 消费线程与定时任务仍需显式配置。

## 4. 核心链路设计

### 4.1 心情打卡

```
App ──POST /api/v1/moods──▶ Nginx ──▶ Gateway
                                      │ ① JWT 验签、Idempotency-Key 去重、Sentinel 限流
                                      ▼
                                 mood-service
                                      │ ② 参数校验 + 唯一索引 (user_id, record_date)
                                      │ ③ 本地事务写 MySQL
                                      │ ④ 发送事务消息 mood-recorded
                                      │ ⑤ 更新 Redis 当日状态
                                      ▼ 返回（目标 <100ms）
                                   RocketMQ
              ┌───────────────┬───────┴────────┬──────────────┐
              ▼               ▼                ▼              ▼
        stats-service   analysis-service   growth-service  message-service
        日统计聚合       连续天数/趋势      积分/任务/成就   成就通知/提醒
                         周报数据预热
```

关键点：

- **同步只做核心落库**，统计、积分、通知全部异步。
- **幂等**：`Idempotency-Key` 存 Redis 24h + 数据库唯一索引双保险。
- **可靠性**：事务消息发送失败时写入本地消息表，由 XXL-Job 补偿。
- **顺序性**：MQ 消息 key 使用 userId，保证同一用户事件顺序消费。

### 4.2 情绪分析与报告

- 打卡事件驱动 `analysis-service` 预计算日统计，趋势页直接读聚合结果。
- 周报/月报由 XXL-Job 批量生成，写入 `t_user_report`，生成完成发通知。
- AI 情绪摘要异步调用模型服务，独立线程池 + 独立限流 + 超时降级，失败不影响主流程。
- 量表结果加密存储，仅用户本人和管理端脱敏视图可见。

### 4.3 社区 Feed（推拉结合）

```
发布 ──▶ community-service 落库(待审核) ──▶ MQ post-published
                                              │
                        ┌─────────────────────┼──────────────────┐
                        ▼                     ▼                  ▼
                   内容审核服务          粉丝收件箱扩散        统计/热榜
                   (机审+人审)        Redis ZSET inbox       Redis ZSET
```

- **普通用户**：写扩散，发布后推送到活跃粉丝的 `feed:inbox:{userId}` ZSET（裁剪 1000 条）。
- **大 V / 热点内容**：读扩散，读取时从作者发件箱拉取后合并。
- **推荐流**：内容池 + 时间衰减热度分，Redis ZSET 缓存，定时刷新。
- **点赞**：Lua 脚本原子判断去重 + `HINCRBY` 计数，RocketMQ 批量回写 MySQL。
- **审核**：公开内容先审后发；私密日记仅自见；审核结果通过 MQ 回写状态。

### 4.4 消息与实时通信

- 在线：`message-service` 内 WebSocket 网关推送，长连接节点通过 Redis 记录路由与在线状态。
- 离线：站内信落库 + 厂商推送通道（APNs/FCM/国内厂商或统一推送平台）。
- 频控：Redis 记录用户/设备推送频次，避免打扰。
- 提醒：RocketMQ 延时消息实现“21:00 未打卡提醒”。
- 长连接扩容：按连接数水平扩展，接入层开启粘性会话。

## 5. 高并发与一致性方案

### 5.1 分层抗压

| 层次  | 手段                                                    |
| --- | ----------------------------------------------------- |
| 接入层 | CDN 静态资源、Nginx `limit_req/limit_conn`、WAF、HTTP/2、连接复用 |
| 网关层 | JWT 本地验签（无状态）、Sentinel QPS/热点参数限流、灰度分流                |
| 服务层 | 全部无状态、水平扩展、线程池隔离、超时控制                               |
| 缓存层 | Caffeine L1 + Redis L2、热点探测、随机 TTL                    |
| 存储层 | 主从读写分离、ShardingSphere 分库分表、慢查询治理                      |
| 消息层 | RocketMQ 削峰、批量消费、消费限流、死信队列                            |

### 5.2 缓存设计

| 场景       | 方案                                               |
| -------- | ------------------------------------------------ |
| 用户/内容详情  | Caffeine(3–10s) + Redis(30–60min 随机 TTL) + MySQL |
| 今日打卡状态   | `CacheKeys.MOOD_TODAY`，过期到次日凌晨                |
| 连续打卡     | Redis Bitmap/Set，异步落库                            |
| 点赞去重     | Redis Set + Lua 原子操作                             |
| Feed 收件箱 | Redis ZSET，score=时间戳                             |
| 热门/榜单    | Redis ZSET                                       |
| 分布式锁     | Redisson（watchdog 续期，Lua 安全释放）                   |
| 接口幂等     | `CacheKeys.IDEMPOTENT`，24h                           |

**三大缓存问题**：穿透 → 空值缓存 + 布隆过滤器；击穿 → 逻辑过期/互斥重建；雪崩 → 随机 TTL + 多级缓存 + Sentinel 兜底。缓存一致性采用 Cache Aside，关键场景可加延迟双删或订阅 Binlog（Canal）刷新。

### 5.3 异步与一致性

- **同步调用**：仅用于用户可感知的查询；设置连接/读超时，禁止长调用链。
- **异步事件**：通知、统计、积分、审核、报告生成全部走 MQ。
- **事务消息 + 本地消息表 + 定时对账**：保证“打卡成功 → 加积分”最终一致。
- **Seata AT** 仅用于无法异步的跨服务强一致场景（如积分兑换），并严格控制范围。
- **消费幂等**：`msgId` 去重表/Redis SETNX + 业务唯一键 + 状态机。
- **顺序性**：同一用户事件使用相同 message key，消费端单线程/分段有序。

### 5.4 MQ Topic 规划

| Topic                  | 生产者       | 消费者                           | 用途           |
| ---------------------- | --------- | ----------------------------- | ------------ |
| `mood-recorded`        | mood      | stats/analysis/growth/message | 打卡派生事件       |
| `post-published`       | community | audit/fanout/stats            | 内容发布与分发      |
| `content-audit-result` | admin/审核  | community/content             | 审核状态回写       |
| `notification`         | 各服务       | message                       | 统一通知出口       |
| `stats-event`          | 各服务       | stats                         | 埋点与运营统计      |
| `points-changed`       | growth    | user/stats                    | 积分变更         |
| `user-logoff`          | user      | 各服务                           | 注销数据清理（Saga） |

统一重试策略：重试 16 次 + 指数退避 → 死信队列 → 告警 + 人工补偿。

### 5.5 分库分表

分片键必须命中查询条件；`t_mood_record`、`t_mood_diary`、`t_post`、`t_message` 按 `user_id` 哈希分片，`t_comment` 按 `post_id` 分片；统一雪花 ID，跨分片统计走 `stats-service` 聚合表。完整规则与表清单见 [`db.md`](db.md) §3。

## 6. 数据模型（核心表）

核心表清单、敏感字段加密、索引与 DDL 规范见 [`db.md`](db.md)。本节只保留两条架构约束：

- 每个服务只写自己的库；禁止跨库 JOIN，跨服务数据走 API / MQ。
- 日记正文、量表结果必须字段级加密（AES-256-GCM），密钥由 KMS/Vault 管理；日志禁止打印正文；备份加密存储。

## 7. 安全与隐私合规

1. **认证授权**：JWT + 不透明 Refresh Token（哈希落库、轮换与撤销）；Access Token 短时有效；`jti` 黑名单与完整 OAuth2.1 作为后续演进（ADR 0003）；管理端独立密钥 + MFA + IP 白名单。
2. **传输与存储**：全站 HTTPS/TLS1.3、HSTS；密码只存 Argon2id/BCrypt 哈希；日记正文、量表结果字段级加密；手机号明文存储但接口与日志必须脱敏（ADR 0003）；数据库最小权限账号。
3. **数据最小化**：只收集必要字段；默认私密；用户可导出、可注销、可要求删除；注销走 Saga 清理各服务数据。
4. **内容安全**：文本敏感词 + 模型审核、语音转写审核、图片审核；公开内容先审后发；举报闭环。
5. **危机干预**：识别自伤/自杀等高风险表达，分级处理——低风险展示关怀内容，中风险展示心理援助资源，高风险进入人工干预流程（操作留审计、严格授权）。
6. **未成年人保护**：未成年人模式、内容分级、时长提醒；不满 14 周岁需监护人同意。
7. **后台安全**：RBAC + 数据权限 + 全操作审计；匿名帖反查需专项授权并留痕。
8. **防刷对抗**：设备指纹、验证码、UID/IP 频控、行为风控、文件类型与大小校验、SSRF 白名单。

## 8. 部署架构（开发环境）

开发环境提供两套后端运行方案，中间件、Nacos 配置与构建产物共用；两套方案端口一致，不可同时启动：

| 方案 | 后端运行方式 | 适用场景 |
| --- | --- | --- |
| A. JVM（默认） | 宿主机 `java -jar` / IDEA | 日常开发、断点调试 |
| B. Docker | 后端容器（`deploy/docker-compose/docker-compose.app.yml`） | 镜像验证、环境一致性联调 |

```
宿主机
├── Docker Compose（deploy/docker-compose/docker-compose.yml，网络 atifah-net）
│   ├── MySQL 8（3306，初始化各服务 Schema）
│   ├── Redis 7（6379）
│   ├── Nacos 3（8848，控制台 8849，配置持久化到 MySQL）
│   ├── RocketMQ（NameServer 9876 / Broker 10911 / Dashboard 8180）
│   ├── Sentinel Dashboard（8858）
│   ├── Seata Server（8091，--profile seata，可选）
│   └── Nginx（80，--profile nginx，可选：本地静态托管验证）
├── 方案 A：atifah-gateway / 业务服务（宿主机 JVM，8080–8090）
│   方案 B：网关与业务服务容器（宿主机端口相同，按需启动）
└── atifah-admin-web/frontend（Vite，5173，代理 /admin/api → 网关）
```

- 启动中间件：`docker compose -f deploy/docker-compose/docker-compose.yml up -d`。
- 方案 A：`mvn clean package -DskipTests` 后 IDE 运行或 `java -jar` 启动网关与业务服务。
- 方案 B：同样先构建 jar，再 `docker compose -f deploy/docker-compose/docker-compose.app.yml up -d --build`；
  镜像由各模块目录下的 `Dockerfile` 从本模块 `target/*.jar` 构建，容器经 `atifah-net` 访问中间件；
  单服务独立部署用各模块自己的 `docker-compose.yml`，统一 compose 复用同一批 Dockerfile。
- 启动前端：`cd atifah-admin-web/frontend && npm run dev`。
- Nacos 共享配置发布到 `atifah_dev`；未发布时业务服务拒绝启动。
- 完整步骤、端口与排障见 [`deploy/dev-deploy.md`](../deploy/dev-deploy.md)。

**本地资源建议**：

| 场景 | 建议配置 | 说明 |
| --- | --- | --- |
| 方案 A（JVM） | 8 vCPU / 16 GB / 200 GB SSD | 全部中间件 + 按需启动服务进程；12 vCPU / 32 GB 更佳 |
| 方案 B（Docker） | 12 vCPU / 32 GB / 200 GB SSD | 单个后端容器默认内存上限 1g，全部 11 个容器上限合计约 11GB，建议按需启动 |

> 生产环境部署清单与云资源不在当前仓库范围；需要时另立 ADR 与 Ops 变更。

## 9. 可观测性与非功能目标

| 指标             | 目标               |
| -------------- | ---------------- |
| 可用性            | 99.95%           |
| 核心读接口 P99      | ≤ 200ms          |
| 核心写接口 P99      | ≤ 300ms          |
| 打卡成功率          | ≥ 99.9%          |
| 核心 MQ 消费延迟 P99 | < 1s（统计类 < 5s）   |
| RPO / RTO      | ≤ 5min / ≤ 30min |

- **链路**：Gateway 生成 TraceId，SkyWalking 全链路追踪，日志统一携带。
- **指标**：JVM、QPS/RT/错误率、Sentinel 指标、MQ 堆积、Redis 命中率、MySQL 慢查询。
- **日志**：JSON 结构化输出到 stdout → Loki，敏感字段脱敏，保留 30–90 天。
- **告警**：错误率、P99 超阈值、MQ 堆积、消费失败、DB 主从延迟、证书到期。

## 10. 演进路线

- **P0（1–2 周）**：工程骨架、Nacos/Gateway/公共模块、docker-compose、CI。
- **P1（2–3 周）**：auth/user/mood/content 核心闭环 + 管理后台 MVP。
- **P2（2–3 周）**：社区、消息推送、增长模块、MQ 事件体系。
- **P3（1–2 周）**：多级缓存、Feed 推拉、点赞计数、分库分表、限流降级专项。
- **P4（2 周）**：可观测性、全链路压测、安全与隐私加固。

**关键取舍**：不要一开始就追求“全量微服务”，低流量服务允许合并部署；强一致范围最小化，优先最终一致 + 对账；缓存和 MQ 必须配套幂等、补偿和监控；情绪数据隐私的优先级高于任何性能优化。
